package com.teamproject.subscription.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.payment.domain.PaymentAttempt;
import com.teamproject.payment.domain.PaymentAttemptRepository;
import com.teamproject.payment.domain.PaymentMethod;
import com.teamproject.payment.domain.PaymentMethodRepository;
import com.teamproject.subscription.domain.GroupSubscription;
import com.teamproject.subscription.domain.GroupSubscriptionRepository;
import com.teamproject.subscription.domain.SubscriptionConsent;
import com.teamproject.subscription.domain.SubscriptionConsentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Keeps every subscription billing database transaction short and free of provider I/O. */
@Service
public class SubscriptionBillingTransactions {
    private final GroupAuthorization authorization;
    private final GroupSubscriptionRepository subscriptions;
    private final PaymentMethodRepository paymentMethods;
    private final PaymentAttemptRepository attempts;
    private final SubscriptionConsentRepository consents;

    public SubscriptionBillingTransactions(GroupAuthorization authorization,
            GroupSubscriptionRepository subscriptions, PaymentMethodRepository paymentMethods,
            PaymentAttemptRepository attempts, SubscriptionConsentRepository consents) {
        this.authorization = authorization;
        this.subscriptions = subscriptions;
        this.paymentMethods = paymentMethods;
        this.attempts = attempts;
        this.consents = consents;
    }

    @Transactional
    public PreparedCharge prepareActivation(Long userId, Long groupId, Long paymentMethodId,
            long amount, String termsVersion, String refundPolicyVersion, String ipAddress,
            String userAgent, LocalDateTime now) {
        GroupMember leader = requireTeamLeader(userId, groupId);
        PaymentMethod method = activeMethod(userId, paymentMethodId);
        GroupSubscription subscription = subscriptions.findByGroupIdForUpdate(groupId)
                .orElseGet(() -> subscriptions.save(new GroupSubscription(
                        leader.getGroup(), leader.getUser(), "TEAM", amount)));
        if (subscription.getConversionChoice() != GroupSubscription.ConversionChoice.CONTINUE_PAID) {
            throw new ApplicationException("SUBSCRIPTION_CONSENT_REQUIRED", HttpStatus.CONFLICT,
                    "유료 전환과 자동결제 조건에 먼저 동의해 주세요.");
        }
        if (subscription.getStatus() == GroupSubscription.Status.ACTIVE
                || subscription.getStatus() == GroupSubscription.Status.CANCEL_AT_PERIOD_END) {
            throw new ApplicationException("SUBSCRIPTION_ALREADY_ACTIVE", HttpStatus.CONFLICT,
                    "이미 활성화된 구독입니다.");
        }
        ensureNoClaim(subscription);
        PreparedCharge charge = claim(subscription, method, now, ChargeKind.ACTIVATION);
        consents.save(new SubscriptionConsent(subscription, leader.getUser(), amount,
                termsVersion, refundPolicyVersion, ipAddress, userAgent, now));
        return charge;
    }

    @Transactional(readOnly = true)
    public List<Long> dueSubscriptionIds(LocalDateTime now, int limit) {
        return subscriptions.findDueIds(now, PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
    }

    @Transactional
    public PreparedCharge prepareRenewal(Long subscriptionId, LocalDateTime now) {
        GroupSubscription subscription = subscriptionForUpdate(subscriptionId);
        if ((subscription.getStatus() != GroupSubscription.Status.ACTIVE
                && subscription.getStatus() != GroupSubscription.Status.PAST_DUE)
                || subscription.getNextBillingAt() == null || subscription.getNextBillingAt().isAfter(now)) {
            return null;
        }
        ensureNoClaim(subscription);
        PaymentMethod method = subscription.getPaymentMethod();
        if (method == null || method.getStatus() != PaymentMethod.Status.ACTIVE) {
            subscription.markPastDue(now);
            return null;
        }
        LocalDateTime periodStart = subscription.getCurrentPeriodEnd() == null
                ? subscription.getNextBillingAt() : subscription.getCurrentPeriodEnd();
        return claim(subscription, method, periodStart, ChargeKind.RENEWAL);
    }

    @Transactional
    public void completeActivation(PreparedCharge charge, LocalDateTime now,
            int providerStatus, String providerPaymentKey) {
        GroupSubscription subscription = claimedSubscription(charge);
        PaymentAttempt attempt = pendingAttempt(charge);
        attempt.success(providerStatus, providerPaymentKey);
        PaymentMethod method = paymentMethods.findById(charge.paymentMethodId()).orElseThrow(() ->
                new ApplicationException("PAYMENT_METHOD_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "결제수단을 찾을 수 없습니다."));
        subscription.activate(method, now);
        subscription.getGroup().applySubscription(Group.MembershipPlan.PAID,
                now, now.plusMonths(1), now.plusMonths(1));
    }

    @Transactional
    public void completeRenewal(PreparedCharge charge, LocalDateTime now,
            int providerStatus, String providerPaymentKey) {
        GroupSubscription subscription = claimedSubscription(charge);
        pendingAttempt(charge).success(providerStatus, providerPaymentKey);
        subscription.renew(now);
        subscription.getGroup().applySubscription(Group.MembershipPlan.PAID,
                now, now.plusMonths(1), now.plusMonths(1));
    }

    @Transactional
    public void recordFailure(PreparedCharge charge, Integer providerStatus, String providerCode,
            String providerMessage, boolean uncertain, LocalDateTime now) {
        GroupSubscription subscription = claimedSubscription(charge);
        PaymentAttempt attempt = pendingAttempt(charge);
        if (uncertain) {
            attempt.unknown(providerStatus, providerCode, providerMessage);
            return; // Keep the claim: only reconciliation may decide whether another charge is safe.
        }
        attempt.fail(providerStatus, providerCode, providerMessage);
        if (charge.kind() == ChargeKind.RENEWAL) subscription.markPastDue(now);
        else subscription.clearBillingClaim();
    }

    @Transactional(readOnly = true)
    public List<UncertainAttempt> uncertainAttempts(LocalDateTime updatedBefore, int limit) {
        return attempts.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        PaymentAttempt.Status.UNKNOWN, updatedBefore,
                        PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
                .stream().filter(value -> value.getSubscriptionId() != null)
                .map(value -> new UncertainAttempt(value.getId(), value.getSubscriptionId(),
                        value.getBusinessKey(), value.getOrderId(), value.getAmount(), value.getBillingKind()))
                .toList();
    }

    @Transactional
    public void reconcileAsPaid(UncertainAttempt uncertain, LocalDateTime now,
            int providerStatus, String providerPaymentKey) {
        PaymentAttempt attempt = attempts.findById(uncertain.attemptId()).orElseThrow(() ->
                new ApplicationException("PAYMENT_ATTEMPT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "결제 호출 기록을 찾을 수 없습니다."));
        if (attempt.getStatus() != PaymentAttempt.Status.UNKNOWN) return;
        GroupSubscription subscription = subscriptionForUpdate(uncertain.subscriptionId());
        if (!uncertain.businessKey().equals(subscription.getBillingClaimKey())) {
            throw new ApplicationException("PAYMENT_CLAIM_CONFLICT", HttpStatus.CONFLICT,
                    "결제 처리 상태가 변경되었습니다.");
        }
        attempt.success(providerStatus, providerPaymentKey);
        if (ChargeKind.ACTIVATION.name().equals(uncertain.billingKind())) {
            PaymentMethod method = attempt.getMethod();
            subscription.activate(method, now);
        } else {
            subscription.renew(now);
        }
        subscription.getGroup().applySubscription(Group.MembershipPlan.PAID,
                now, now.plusMonths(1), now.plusMonths(1));
    }

    private PreparedCharge claim(GroupSubscription subscription, PaymentMethod method,
            LocalDateTime periodStart, ChargeKind kind) {
        String claimKey = "subscription:" + subscription.getId() + ":" + kind.name().toLowerCase()
                + ":" + periodStart + ":" + UUID.randomUUID();
        String idempotencyKey = "sub-" + subscription.getId() + "-" + UUID.randomUUID();
        String orderId = "sub-" + subscription.getId() + "-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        subscription.claimBilling(claimKey, now);
        PaymentAttempt attempt = attempts.save(new PaymentAttempt(subscription.getSubscriber(), method,
                subscription.getId(), PaymentAttempt.OperationType.SUBSCRIPTION_CHARGE,
                idempotencyKey, claimKey, periodStart, kind.name(), orderId, subscription.getAmount()));
        return new PreparedCharge(subscription.getId(), subscription.getGroup().getId(),
                subscription.getSubscriber().getId(), method.getId(), method.getEncryptedBillingKey(),
                subscription.getSubscriber().getPaymentCustomerKey(), attempt.getId(), claimKey,
                idempotencyKey, orderId, subscription.getAmount(), periodStart, kind);
    }

    private void ensureNoClaim(GroupSubscription subscription) {
        ensureNoBillingClaim(subscription);
        if (subscription.getSubscriber().getPaymentCustomerKey() == null) {
            throw new ApplicationException("PAYMENT_CUSTOMER_NOT_READY", HttpStatus.CONFLICT,
                    "결제 고객 정보를 먼저 생성해 주세요.");
        }
    }

    private void ensureNoBillingClaim(GroupSubscription subscription) {
        if (subscription.getBillingClaimKey() != null) {
            throw new ApplicationException("PAYMENT_RECONCILIATION_REQUIRED", HttpStatus.CONFLICT,
                    "이 구독의 이전 결제 결과를 확인하고 있습니다.");
        }
    }

    private GroupSubscription claimedSubscription(PreparedCharge charge) {
        GroupSubscription subscription = subscriptionForUpdate(charge.subscriptionId());
        if (!charge.claimKey().equals(subscription.getBillingClaimKey())) {
            throw new ApplicationException("PAYMENT_CLAIM_CONFLICT", HttpStatus.CONFLICT,
                    "결제 처리 상태가 변경되었습니다.");
        }
        return subscription;
    }

    private PaymentAttempt pendingAttempt(PreparedCharge charge) {
        PaymentAttempt attempt = attempts.findById(charge.attemptId()).orElseThrow(() ->
                new ApplicationException("PAYMENT_ATTEMPT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "결제 호출 기록을 찾을 수 없습니다."));
        if (attempt.getStatus() != PaymentAttempt.Status.PENDING) {
            throw new ApplicationException("PAYMENT_ATTEMPT_ALREADY_RESOLVED", HttpStatus.CONFLICT,
                    "이미 처리된 결제 요청입니다.");
        }
        return attempt;
    }

    private GroupSubscription subscriptionForUpdate(Long subscriptionId) {
        return subscriptions.findByIdForUpdate(subscriptionId).orElseThrow(() ->
                new ApplicationException("SUBSCRIPTION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "구독 정보를 찾을 수 없습니다."));
    }

    private PaymentMethod activeMethod(Long userId, Long methodId) {
        return paymentMethods.findByIdAndUserId(methodId, userId)
                .filter(value -> value.getStatus() == PaymentMethod.Status.ACTIVE)
                .orElseThrow(() -> new ApplicationException("PAYMENT_METHOD_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "결제수단을 찾을 수 없습니다."));
    }

    private GroupMember requireTeamLeader(Long userId, Long groupId) {
        GroupMember member = authorization.requireLeader(groupId, userId);
        if (member.getGroup().getType() != Group.Type.TEAM) {
            throw new ApplicationException("PERSONAL_GROUP_RESTRICTED", HttpStatus.BAD_REQUEST,
                    "팀 그룹에서만 구독을 사용할 수 있습니다.");
        }
        return member;
    }

    public enum ChargeKind { ACTIVATION, RENEWAL }

    public record PreparedCharge(Long subscriptionId, Long groupId, Long userId, Long paymentMethodId,
            String encryptedBillingKey, String customerKey, Long attemptId, String claimKey,
            String idempotencyKey, String orderId, long amount, LocalDateTime billingPeriodStart,
            ChargeKind kind) {}

    public record UncertainAttempt(Long attemptId, Long subscriptionId, String businessKey,
            String orderId, Long amount, String billingKind) {}
}
