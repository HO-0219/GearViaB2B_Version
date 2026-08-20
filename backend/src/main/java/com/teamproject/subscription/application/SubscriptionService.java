package com.teamproject.subscription.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.common.scheduling.DatabaseJobLock;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.subscription.application.dto.SubscriptionDtos.*;
import com.teamproject.subscription.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

@Service
public class SubscriptionService {
    private static final String PAID_TERMS_VERSION = "2026-07-27-v3";
    private static final String REFUND_POLICY_VERSION = "2026-07-27-v3";
    private final GroupAuthorization authorization;
    private final GroupSubscriptionRepository subscriptions;
    private final SubscriptionBillingCoordinator billing;
    private final boolean liveBillingEnabled;
    private final int trialDays;
    private final long teamMonthlyPrice;
    private final int paymentGraceDays;
    private final DatabaseJobLock jobLock;
    public SubscriptionService(GroupAuthorization authorization, GroupSubscriptionRepository subscriptions,
            SubscriptionBillingCoordinator billing, DatabaseJobLock jobLock,
            @Value("${app.subscription.live-billing-enabled:false}") boolean liveBillingEnabled,
            @Value("${app.subscription.trial-days:30}") int trialDays,
            @Value("${app.subscription.team-monthly-price:9900}") long teamMonthlyPrice,
            @Value("${app.subscription.payment-grace-days:7}") int paymentGraceDays) {
        this.authorization = authorization; this.subscriptions = subscriptions;
        this.billing = billing; this.jobLock = jobLock;
        this.liveBillingEnabled = liveBillingEnabled; this.trialDays = trialDays;
        this.teamMonthlyPrice = teamMonthlyPrice; this.paymentGraceDays = paymentGraceDays;
    }
    @Transactional
    public SubscriptionResponse get(Long userId, Long groupId) {
        return response(subscription(requireTeamLeader(userId, groupId)));
    }
    @Transactional
    public SubscriptionResponse startTrial(Long userId, Long groupId) {
        GroupMember leader = requireTeamLeader(userId, groupId);
        GroupSubscription value = subscriptionForUpdate(leader);
        requireNoBillingClaim(value);
        if (value.getStatus() != GroupSubscription.Status.FREE || value.getCurrentPeriodStart() != null) {
            throw new ApplicationException("SUBSCRIPTION_TRIAL_NOT_AVAILABLE", HttpStatus.CONFLICT, "무료 체험을 시작할 수 없습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        value.startTrial(now, trialDays);
        leader.getGroup().applySubscription(Group.MembershipPlan.PAID, now, now.plusDays(trialDays), null);
        return response(value);
    }
    @Transactional
    public SubscriptionResponse choose(Long userId, Long groupId, String rawChoice) {
        GroupMember leader = requireTeamLeader(userId, groupId);
        GroupSubscription value = subscriptionForUpdate(leader);
        requireNoBillingClaim(value);
        GroupSubscription.ConversionChoice choice;
        try { choice = GroupSubscription.ConversionChoice.valueOf(rawChoice.trim().toUpperCase()); }
        catch (RuntimeException exception) {
            throw new ApplicationException("SUBSCRIPTION_CHOICE_INVALID", HttpStatus.BAD_REQUEST, "유지 또는 무료 전환을 선택해 주세요.");
        }
        if (choice == GroupSubscription.ConversionChoice.UNDECIDED) {
            throw new ApplicationException("SUBSCRIPTION_CHOICE_INVALID", HttpStatus.BAD_REQUEST, "결정된 전환 방식을 선택해 주세요.");
        }
        value.choose(choice, LocalDateTime.now());
        if (choice == GroupSubscription.ConversionChoice.KEEP_FREE) {
            leader.getGroup().applySubscription(Group.MembershipPlan.FREE, null, null, null);
        }
        return response(value);
    }
    public SubscriptionResponse activate(Long userId, Long groupId, ActivateSubscriptionRequest request,
            String ipAddress, String userAgent) {
        if (!liveBillingEnabled && !billing.testPaymentsEnabled()) {
            throw new ApplicationException("LIVE_BILLING_NOT_OPEN", HttpStatus.CONFLICT,
                    "사업자·통신판매업 및 운영 결제 승인이 완료된 뒤 유료 전환할 수 있습니다.");
        }
        if (!request.recurringBillingConsent() || !request.policyConsent()
                || !PAID_TERMS_VERSION.equals(request.termsVersion())
                || !REFUND_POLICY_VERSION.equals(request.refundPolicyVersion())) {
            throw new ApplicationException("SUBSCRIPTION_CONSENT_REQUIRED", HttpStatus.CONFLICT,
                    "최신 유료서비스 약관, 환불 정책과 자동결제에 동의해 주세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        billing.activate(userId, groupId, request.paymentMethodId(), teamMonthlyPrice,
                PAID_TERMS_VERSION, REFUND_POLICY_VERSION, ipAddress, userAgent, now);
        return get(userId, groupId);
    }
    @Transactional
    public SubscriptionResponse cancel(Long userId, Long groupId) {
        GroupMember leader = requireTeamLeader(userId, groupId);
        GroupSubscription value = subscriptionForUpdate(leader);
        requireNoBillingClaim(value);
        if (value.getStatus() != GroupSubscription.Status.FREE) value.cancelAtPeriodEnd(LocalDateTime.now());
        return response(value);
    }
    @Scheduled(cron = "${app.subscription.lifecycle-cron:0 15 3 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void closeExpiredPeriods() {
        if (!jobLock.acquire("subscription-lifecycle", Duration.ofMinutes(55))) return;
        LocalDateTime now = LocalDateTime.now();
        subscriptions.findAllByStatusInAndCurrentPeriodEndLessThanEqual(
                List.of(GroupSubscription.Status.TRIALING, GroupSubscription.Status.CANCEL_AT_PERIOD_END), now)
                .stream().filter(value -> value.getBillingClaimKey() == null)
                .forEach(value -> {
                    value.cancelToFree(now);
                    value.getGroup().applySubscription(Group.MembershipPlan.FREE, null, null, null);
                });
        subscriptions.findAllByStatusAndPastDueSinceLessThanEqual(
                GroupSubscription.Status.PAST_DUE, now.minusDays(paymentGraceDays)).stream()
                .filter(value -> value.getBillingClaimKey() == null).forEach(value -> {
                    value.cancelToFree(now);
                    value.getGroup().applySubscription(Group.MembershipPlan.FREE, null, null, null);
                });
    }
    @Scheduled(cron = "${app.subscription.billing-cron:0 5 * * * *}", zone = "Asia/Seoul")
    public void chargeDueSubscriptions() {
        if (!liveBillingEnabled) return;
        if (!jobLock.acquire("subscription-billing", Duration.ofMinutes(55))) return;
        billing.chargeDue(LocalDateTime.now(), 500);
    }
    @Scheduled(cron = "${app.subscription.reconciliation-cron:0 */10 * * * *}", zone = "Asia/Seoul")
    public void reconcileUncertainPayments() {
        if (!liveBillingEnabled) return;
        if (!jobLock.acquire("subscription-reconciliation", Duration.ofMinutes(9))) return;
        billing.reconcileUnknown(LocalDateTime.now(), Duration.ofMinutes(2), 100);
    }
    private GroupMember requireTeamLeader(Long userId, Long groupId) {
        GroupMember member = authorization.requireLeader(groupId, userId);
        if (member.getGroup().getType() != Group.Type.TEAM) {
            throw new ApplicationException("PERSONAL_GROUP_RESTRICTED", HttpStatus.BAD_REQUEST, "팀 그룹에서만 구독을 사용할 수 있습니다.");
        }
        return member;
    }
    private GroupSubscription subscription(GroupMember leader) {
        return subscriptions.findByGroupId(leader.getGroup().getId())
                .orElseGet(() -> subscriptions.save(new GroupSubscription(leader.getGroup(), leader.getUser(), "TEAM", teamMonthlyPrice)));
    }
    private GroupSubscription subscriptionForUpdate(GroupMember leader) {
        return subscriptions.findByGroupIdForUpdate(leader.getGroup().getId())
                .orElseGet(() -> subscriptions.save(new GroupSubscription(
                        leader.getGroup(), leader.getUser(), "TEAM", teamMonthlyPrice)));
    }
    private void requireNoBillingClaim(GroupSubscription value) {
        if (value.getBillingClaimKey() != null) {
            throw new ApplicationException("PAYMENT_RECONCILIATION_REQUIRED", HttpStatus.CONFLICT,
                    "결제 결과를 확인하는 동안 구독 설정을 변경할 수 없습니다.");
        }
    }
    private SubscriptionResponse response(GroupSubscription value) {
        return new SubscriptionResponse(value.getId(), value.getGroup().getId(), value.getPlanCode(),
                value.getStatus().name(), value.getAmount(), value.getCurrency(), value.getConversionChoice().name(),
                value.getRolloutNoticeAt(), value.getDecisionDeadline(), value.getCurrentPeriodStart(),
                value.getCurrentPeriodEnd(), value.getNextBillingAt(),
                liveBillingEnabled || billing.testPaymentsEnabled(),
                value.getStatus() == GroupSubscription.Status.FREE && value.getCurrentPeriodStart() == null);
    }
}
