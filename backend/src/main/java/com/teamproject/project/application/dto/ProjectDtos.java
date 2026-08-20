package com.teamproject.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record CreateProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 5000) String description,
            Long leadMemberId, LocalDate startDate, LocalDate dueDate) {}

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 5000) String description,
            Long leadMemberId, @NotBlank String status,
            LocalDate startDate, LocalDate dueDate,
            @NotNull Long expectedVersion) {}

    public record ProjectResponse(
            Long id, Long groupId, String name, String description, String status,
            Long leadMemberId, String leadNickname,
            Long createdByMemberId, String createdByNickname,
            LocalDate startDate, LocalDate dueDate,
            long version, LocalDateTime createdAt, LocalDateTime updatedAt,
            boolean canManage, boolean canManageFlow) {}
}
