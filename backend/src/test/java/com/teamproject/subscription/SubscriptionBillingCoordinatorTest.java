package com.teamproject.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.payment.infrastructure.PaymentSecretCipher;
import com.teamproject.payment.infrastructure.TossPaymentsClient;
import com.teamproject.subscription.application.SubscriptionBillingCoordinator;
import com.teamproject.subscription.application.SubscriptionBillingTransactions;
import com.teamproject.subscription.application.SubscriptionBillingTransactions.ChargeKind;
import com.teamproject.subscription.application.SubscriptionBillingTransactions.PreparedCharge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionBillingCoordinatorTest {
    @Mock SubscriptionBillingTransactions transactions;
    @Mock TossPaymentsClient toss;
    @Mock PaymentSecretCipher cipher;
    private SubscriptionBillingCoordinator coordinator;
    private PreparedCharge charge;

    @BeforeEach
    void setUp() {
        coordinator = new SubscriptionBillingCoordinator(transactions, toss, cipher);
        charge = new PreparedCharge(1L, 2L, 3L, 4L, "encrypted", "customer", 5L,
                "claim", "idempotency", "order", 9900L,
                LocalDateTime.of(2026, 8, 1, 0, 0), ChargeKind.ACTIVATION);
        when(toss.configured()).thenReturn(true);
        when(cipher.configured()).thenReturn(true);
        lenient().when(cipher.decrypt("encrypted")).thenReturn("billing-key");
    }

    @Test
    void providerCallRunsBetweenClaimAndSuccessTransactions() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 12, 0);
        when(transactions.prepareActivation(3L, 2L, 4L, 9900L,
                "terms", "refund", "127.0.0.1", "test", now)).thenReturn(charge);
        when(toss.charge("billing-key", "customer", 9900L, "order", "Gearvia 팀 구독", "idempotency"))
                .thenReturn(new TossPaymentsClient.ApiResult(200, new ObjectMapper().createObjectNode(), null, null));

        coordinator.activate(3L, 2L, 4L, 9900L, "terms", "refund", "127.0.0.1", "test", now);

        var ordered = inOrder(transactions, toss);
        ordered.verify(transactions).prepareActivation(3L, 2L, 4L, 9900L,
                "terms", "refund", "127.0.0.1", "test", now);
        ordered.verify(toss).charge("billing-key", "customer", 9900L, "order", "Gearvia 팀 구독", "idempotency");
        ordered.verify(transactions).completeActivation(charge, now, 200, null);
        verify(transactions, never()).recordFailure(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void missingProviderResponseStopsAutomaticRetryAndRequiresReconciliation() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 12, 0);
        when(transactions.prepareActivation(3L, 2L, 4L, 9900L,
                "terms", "refund", "127.0.0.1", "test", now)).thenReturn(charge);
        when(toss.charge(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new TossPaymentsClient.ApiResult(null, new ObjectMapper().createObjectNode(),
                        "TOSS_NETWORK_ERROR", "network error"));

        assertThatThrownBy(() -> coordinator.activate(
                3L, 2L, 4L, 9900L, "terms", "refund", "127.0.0.1", "test", now))
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo("PAYMENT_RECONCILIATION_REQUIRED"));

        verify(transactions).recordFailure(charge, null, "TOSS_NETWORK_ERROR", "network error", true, now);
        verify(transactions, never()).completeActivation(any(), any(), anyInt(), any());
    }

}
