package com.teamproject.calendar.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class CalendarDtos {
    private CalendarDtos() {}

    public record CreateCalendarEventRequest(
            @NotBlank @Size(max = 20) String type,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) String description,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt,
            boolean allDay,
            @Size(max = 300) String location) {}

    public record UpdateCalendarEventRequest(
            @Size(max = 20) String type,
            @Size(max = 160) String title,
            @Size(max = 2000) String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Boolean allDay,
            @Size(max = 300) String location,
            Boolean clearDescription,
            Boolean clearLocation,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record CalendarItemResponse(
            String source, Long eventId, Long sourceTaskId, Long groupId, String groupName,
            String groupType, String timezone, String type, String title, String description,
            LocalDateTime startAt, LocalDateTime endAt, Instant startAtUtc, Instant endAtUtc,
            boolean allDay, String location, Long createdByMemberId, Long version,
            LocalDateTime createdAt, LocalDateTime updatedAt,
            Long ownerMemberId, String ownerNickname,
            String taskStatus, boolean taskDelayed,
            Long taskAssigneeMemberId, String taskAssigneeNickname) {}

    public record CalendarResponse(List<CalendarItemResponse> items, String from, String to) {}
}
