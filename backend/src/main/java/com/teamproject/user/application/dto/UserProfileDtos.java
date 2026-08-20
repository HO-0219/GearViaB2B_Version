package com.teamproject.user.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class UserProfileDtos {
    private UserProfileDtos() {}

    public record ProfileResponse(
            Long userId,
            String username,
            String email,
            String name,
            String nickname,
            String phoneNumber,
            String profileImageUrl,
            String status,
            String systemRole,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        @Override public String toString() {
            return "ProfileResponse[userId=" + userId + ", status=" + status + ", systemRole=" + systemRole + "]";
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 30) String nickname,
            @Pattern(regexp = "^$|^[0-9+() -]{7,20}$", message = "전화번호 형식을 확인해 주세요.") String phoneNumber,
            @Size(max = 500) String profileImageUrl
    ) {
        @Override public String toString() { return "UpdateProfileRequest[redacted]"; }
    }
}
