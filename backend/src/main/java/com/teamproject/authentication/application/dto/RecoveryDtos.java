package com.teamproject.authentication.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class RecoveryDtos {
    private RecoveryDtos() {}

    public record UsernameReminderRequest(@NotBlank @Email String email) {
        @Override public String toString() { return "UsernameReminderRequest[redacted]"; }
    }
    public record PasswordResetRequest(@NotBlank @Email String email) {
        @Override public String toString() { return "PasswordResetRequest[redacted]"; }
    }
    public record PasswordResetConfirmRequest(
            @NotBlank @Email String email,
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {
        @Override public String toString() { return "PasswordResetConfirmRequest[redacted]"; }
    }
}
