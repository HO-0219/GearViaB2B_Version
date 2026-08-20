package com.teamproject.project.application.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectIssueDtos {
    private ProjectIssueDtos() {}

    public record CreateIssueNodeRequest(
            @NotBlank String level,
            Long parentId,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 10000) String description,
            Long assigneeMemberId,
            @Min(0) Integer sortOrder,
            LocalDate dueDate) {}

    public record UpdateIssueNodeRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 10000) String description,
            Long assigneeMemberId,
            @NotBlank String status,
            @Min(0) int sortOrder,
            LocalDate dueDate,
            @NotNull Long expectedVersion) {}

    public record CreateIssueChecklistRequest(
            @NotBlank @Size(max = 500) String content,
            @Min(0) Integer sortOrder) {}

    public record UpdateIssueChecklistRequest(
            @Size(min = 1, max = 500) String content,
            Boolean completed,
            @Min(0) Integer sortOrder,
            @NotNull Long expectedVersion) {}

    public record IssueChecklistResponse(
            Long id, String content, boolean completed, Long completedByMemberId,
            LocalDateTime completedAt, int sortOrder, long version,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record IssueImageResponse(
            Long id, String originalFilename, String contentType, long sizeBytes,
            Long uploadedByMemberId, String uploadedByNickname, int sortOrder,
            LocalDateTime createdAt, String contentUrl, boolean canDelete) {}

    public record IssueNodeResponse(
            Long id, Long projectId, Long parentId, String level, String title, String description,
            String status, Long assigneeMemberId, String assigneeNickname,
            Long createdByMemberId, String createdByNickname, int sortOrder, LocalDate dueDate,
            long version, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime archivedAt,
            boolean canManage, List<IssueChecklistResponse> checklist, List<IssueImageResponse> images) {}
}
