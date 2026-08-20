package com.teamproject.subscription.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.payment.infrastructure.PaymentSecretCipher;
import com.teamproject.payment.infrastructure.TossPaymentsClient;
import com.teamproject.subscription.application.SubscriptionBillingTransactions.PreparedCharge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;

/** Orchestrates provider I/O only after the short claim transaction has committed. */
@Service
public class SubscriptionBillingCoordinator {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingCoordinator.class);
    private final SubscriptionBillingTransactions transactions;
    private final TossPaymentsClient toss;
    private final PaymentSecretCipher cipher;

    public SubscriptionBillingCoordinator(SubscriptionBillingTransactions transactions,
            TossPaymentsClient toss, PaymentSecretCipher cipher) {
        this.transactions = transactions;
        this.toss = toss;
        this.cipher = cipher;
    }

    public boolean testPaymentsEnabled() { return toss.usesOfficialTestKey(); }

    public void activate(Long userId, Long groupId, Long paymentMethodId, long amount,
            String termsVersion, String refundPolicyVersion, String ipAddress, String userAgent,
            LocalDateTime now) {
        requireConfigured();
        PreparedCharge charge = transactions.prepareActivation(userId, groupId, paymentMethodId, amount,
                termsVersion, refundPolicyVersion, ipAddress, userAgent, now);
        TossPaymentsClient.ApiResult result = callProvider(charge);
        if (!result.successful()) {
            fail(charge, result, now);
        }
        transactions.completeActivation(charge, now, result.status(), paymentKey(result));
    }

    public int chargeDue(LocalDateTime now, int limit) {
        requireConfigured();
        int processed = 0;
        for (Long subscriptionId : transactions.dueSubscriptionIds(now, limit)) {
            try {
                PreparedCharge charge = transactions.prepareRenewal(subscriptionId, now);
                if (charge == null) continue;
                TossPaymentsClient.ApiResult result = callProvider(charge);
                if (!result.successful()) {
                    fail(charge, result, now);
                    continue;
                }
                transactions.completeRenewal(charge, now, result.status(), paymentKey(result));
                processed++;
            } catch (ApplicationException exception) {
                log.warn("Subscription billing skipped: subscriptionId={} code={}",
                        subscriptionId, exception.code());
            } catch (RuntimeException exception) {
                log.error("Subscription billing failed after claim: subscriptionId={} exception={}",
                        subscriptionId, exception.getClass().getSimpleName());
            }
        }
        return processed;
    }

    public int reconcileUnknown(LocalDateTime now, Duration minimumAge, int limit) {
        requireConfigured();
        int reconciled = 0;
        for (var uncertain : transactions.uncertainAttempts(now.minus(minimumAge), limit)) {
            TossPaymentsClient.ApiResult result = toss.findPaymentByOrderId(uncertain.orderId());
            if (!result.successful()) {
                log.warn("Payment reconciliation lookup unresolved: attemptId={} httpStatus={} code={}",
                        uncertain.attemptId(), result.status(), result.errorCode());
                continue;
            }
            String status = result.body().path("status").asText("");
            String orderId = result.body().path("orderId").asText("");
            long amount = result.body().has("totalAmount")
                    ? result.body().path("totalAmount").asLong(-1)
                    : result.body().path("amount").asLong(-1);
            if (!"DONE".equals(status) || !uncertain.orderId().equals(orderId)
                    || amount != uncertain.amount()) {
                log.error("Payment reconciliation mismatch: attemptId={} status={} orderMatches={} amountMatches={}",
                        uncertain.attemptId(), status, uncertain.orderId().equals(orderId),
                        amount == uncertain.amount());
                continue;
            }
            transactions.reconcileAsPaid(uncertain, now, result.status(), paymentKey(result));
            reconciled++;
        }
        return reconciled;
    }

    private TossPaymentsClient.ApiResult callProvider(PreparedCharge charge) {
        return toss.charge(cipher.decrypt(charge.encryptedBillingKey()), charge.customerKey(),
                charge.amount(), charge.orderId(), "B2BGearVia 팀 구독", charge.idempotencyKey());
    }

    private void fail(PreparedCharge charge, TossPaymentsClient.ApiResult result, LocalDateTime now) {
        boolean uncertain = result.status() == null || result.status() >= 500;
        transactions.recordFailure(charge, result.status(), result.errorCode(),
                result.errorMessage(), uncertain, now);
        if (uncertain) {
            throw new ApplicationException("PAYMENT_RECONCILIATION_REQUIRED", HttpStatus.SERVICE_UNAVAILABLE,
                    "결제 결과를 즉시 확인하지 못해 자동 재결제를 중단했습니다.");
        }
        throw new ApplicationException(result.errorCode(), HttpStatus.BAD_GATEWAY, result.errorMessage());
    }

    private void requireConfigured() {
        if (!toss.configured() || !cipher.configured()) {
            throw new ApplicationException("PAYMENT_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                    "결제 연동 환경변수가 설정되지 않았습니다.");
        }
    }

    private String paymentKey(TossPaymentsClient.ApiResult result) {
        String value = result.body().path("paymentKey").asText("");
        return value.isBlank() ? null : value;
    }
}
