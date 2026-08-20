package com.teamproject.task.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class ChecklistDtos {
    private ChecklistDtos() {}

    public record CreateChecklistItemRequest(
            @NotBlank @Size(max = 300) String content,
            @PositiveOrZero Integer sortOrder) {}

    public record UpdateChecklistItemRequest(
            @Size(max = 300) String content,
            Boolean completed,
            @PositiveOrZero Integer sortOrder,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record ChecklistItemResponse(
            Long id, Long taskId, String content, boolean completed,
            Long completedByMemberId, LocalDateTime completedAt, int sortOrder,
            long version, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record ChecklistResponse(
            List<ChecklistItemResponse> items, int totalCount, int completedCount,
            Integer progressPercent) {}
}
