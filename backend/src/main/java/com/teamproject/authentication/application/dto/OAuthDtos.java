package com.teamproject.authentication.application.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public final class OAuthDtos {
    private OAuthDtos() {}
    public record ProviderResponse(boolean google, boolean kakao) {}
    public record SignupStatusResponse(String provider, String email, String name, LocalDateTime expiresAt) {
        @Override public String toString() { return "SignupStatusResponse[provider=" + provider + ", redacted]"; }
    }
    public record SignupCompleteRequest(
            @NotNull @AssertTrue Boolean termsAgreed,
            @NotNull @AssertTrue Boolean privacyAgreed,
            @NotNull @AssertTrue Boolean ageConfirmed,
            @NotNull Boolean notificationAgreed,
            @NotNull Boolean marketingAgreed) {
        @Override public String toString() { return "SignupCompleteRequest[consents=redacted]"; }
    }
}
