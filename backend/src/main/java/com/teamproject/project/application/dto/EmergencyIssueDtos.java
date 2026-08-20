package com.teamproject.project.application.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public final class EmergencyIssueDtos {
    private EmergencyIssueDtos() {}
    public record CreateRequest(@NotNull @Positive Long projectId, @NotBlank @Size(max=160) String title,
            @Size(max=5000) String description, @NotBlank String audience) {}
    public record StatusRequest(@NotBlank String status, @NotNull @PositiveOrZero Long expectedVersion) {}
    public record Response(Long id, Long groupId, Long projectId, String projectName, Long createdByMemberId,
            String createdByNickname, String title, String description, String audience, String status,
            String imageUrl, LocalDateTime resolvedAt, LocalDateTime createdAt, LocalDateTime updatedAt,
            long version, boolean canManage) {}
}
