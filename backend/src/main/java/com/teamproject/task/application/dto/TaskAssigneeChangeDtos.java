package com.teamproject.task.application.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public final class TaskAssigneeChangeDtos {
    private TaskAssigneeChangeDtos() {}
    public record CreateRequest(@NotNull @Positive Long assigneeMemberId, @Size(max=500) String reason) {}
    public record DecisionRequest(@NotBlank String decision, @Size(max=500) String note, @NotNull @PositiveOrZero Long expectedVersion) {}
    public record Response(Long id, Long taskId, String taskTitle, Long requestedByMemberId, String requestedByNickname,
            Long proposedAssigneeMemberId, String proposedAssigneeNickname, String status, String reason,
            Long reviewedByMemberId, String reviewNote, LocalDateTime createdAt, LocalDateTime reviewedAt, long version) {}
}
