package com.teamproject.authentication.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public final class SignupDtos {
    private SignupDtos() {}

    public record VerificationEmailRequest(@NotBlank @Email String email) {
        @Override public String toString() { return "VerificationEmailRequest[redacted]"; }
    }
    public record VerificationConfirmRequest(@NotBlank @Email String email,
            @Pattern(regexp = "\\d{6}") String code) {
        @Override public String toString() { return "VerificationConfirmRequest[redacted]"; }
    }
    public record SignupRequest(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$") String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 2, max = 60) String name,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Pattern(regexp = "\\d{6}") String verificationCode,
            @NotNull @AssertTrue Boolean termsAgreed,
            @NotNull @AssertTrue Boolean privacyAgreed,
            @NotNull @AssertTrue Boolean ageConfirmed,
            @NotNull Boolean notificationAgreed,
            @NotNull Boolean marketingAgreed
    ) {
        public SignupRequest(String username, String email, String name, String password, String verificationCode) {
            this(username, email, name, password, verificationCode, true, true, true, false, false);
        }
        @Override public String toString() { return "SignupRequest[redacted]"; }
    }
    public record SignupResponse(Long userId, String username, String email, String name) {
        @Override public String toString() { return "SignupResponse[userId=" + userId + "]"; }
    }
}
