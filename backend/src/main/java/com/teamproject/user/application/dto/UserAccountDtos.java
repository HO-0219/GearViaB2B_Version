package com.teamproject.user.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserAccountDtos {
    private UserAccountDtos() {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {
        @Override public String toString() { return "ChangePasswordRequest[redacted]"; }
    }

    public record WithdrawRequest(String currentPassword) {
        @Override public String toString() { return "WithdrawRequest[redacted]"; }
    }
}
