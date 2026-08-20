package com.teamproject.payment.application.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class PaymentDtos {
    private PaymentDtos() {}
    public record PaymentConfigResponse(boolean configured, boolean testMode, String clientKey, String customerKey) {}
    public record IssuePaymentMethodRequest(
            @NotBlank @Size(max = 300) String authKey,
            @NotBlank @Size(max = 300) String customerKey) {
        @Override public String toString() { return "IssuePaymentMethodRequest[redacted]"; }
    }
    public record PaymentMethodResponse(Long id, String provider, String issuerCode, String maskedNumber,
            String status, LocalDateTime createdAt) {}
    public record TestChargeRequest(@Min(100) @Max(10000) long amount) {}
    public record PaymentAttemptResponse(Long id, Long paymentMethodId, String operationType, String orderId,
            Long amount, String status, Integer httpStatus, String providerCode, String providerMessage,
            int retryCount, LocalDateTime createdAt) {}
}
