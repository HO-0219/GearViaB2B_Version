package com.teamproject.admin.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}
    public record OverviewResponse(long users, long activeUsers, long suspendedUsers,
            long groups, long teamGroups, long reportDownloads, long reportDeliveries,
            long failedReportDeliveries) {}
    public record AdminUserResponse(Long id, String username, String maskedEmail, String nickname,
            String role, String status, LocalDateTime createdAt, LocalDateTime lastLoginAt, boolean forcePasswordChange) {}
    public record CreateUserRequest(@NotBlank @Email String email, @NotBlank String name, String role) {}
    public record TemporaryPasswordResponse(AdminUserResponse user, String temporaryPassword) {}
    public record AdminGroupResponse(Long id, String name, String type,
            long activeMembers, boolean reportScheduleActive, LocalDateTime createdAt) {}
    public record AdminAuditResponse(Long id, Long actorUserId, String method, String path,
            int status, String outcome, String ipAddress, String requestId, LocalDateTime occurredAt) {}
    public record AdminReportDownloadResponse(Long id, Long groupId, String groupName,
            Long requestedByUserId, String scope, String periodType, LocalDateTime createdAt) {}
    public record AdminReportDeliveryResponse(Long id, Long groupId, String groupName,
            String periodType, String language, String status, int retryCount, String errorCode,
            LocalDateTime lastAttemptAt, LocalDateTime nextRetryAt, LocalDateTime sentAt,
            LocalDateTime createdAt) {}
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
