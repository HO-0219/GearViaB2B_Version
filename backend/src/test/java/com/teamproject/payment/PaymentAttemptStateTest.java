package com.teamproject.payment;

import com.teamproject.payment.domain.PaymentAttempt;
import com.teamproject.payment.domain.PaymentMethod;
import com.teamproject.user.domain.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAttemptStateTest {
    @Test
    void uncertainProviderResultIsNotRecordedAsSafeToRetryFailure() {
        User user = new User("billing-user", "billing@example.com", "hash", "결제 사용자", true);
        PaymentMethod method = new PaymentMethod(user, "encrypted", "issuer", "1234");
        PaymentAttempt attempt = new PaymentAttempt(user, method, 11L,
                PaymentAttempt.OperationType.SUBSCRIPTION_CHARGE,
                "provider-key", "subscription:11:period:1", LocalDateTime.of(2026, 8, 1, 0, 0), "RENEWAL",
                "order-1", 9900L);

        attempt.unknown(null, "TOSS_NETWORK_ERROR", "provider response was not received");

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttempt.Status.UNKNOWN);
        assertThat(attempt.getBusinessKey()).isEqualTo("subscription:11:period:1");
        assertThat(attempt.getSubscriptionId()).isEqualTo(11L);
    }
}
