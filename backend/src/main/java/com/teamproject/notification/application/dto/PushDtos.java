package com.teamproject.notification.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PushDtos {
    private PushDtos() {}

    public record PushConfigResponse(boolean enabled, String publicKey, boolean consentAgreed) {}
    public record PushSubscriptionRequest(
            @NotBlank @Size(max = 2048) String endpoint,
            @NotBlank @Size(max = 255) String p256dh,
            @NotBlank @Size(max = 255) String auth) {}
    public record PushUnsubscribeRequest(@NotBlank @Size(max = 2048) String endpoint) {}
}
