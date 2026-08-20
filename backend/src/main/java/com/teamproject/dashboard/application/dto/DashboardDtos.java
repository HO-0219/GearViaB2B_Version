package com.teamproject.dashboard.application.dto;

import com.teamproject.calendar.application.dto.CalendarDtos.CalendarItemResponse;
import com.teamproject.notification.application.dto.NotificationDtos.NotificationResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record StatusCounts(long requested, long todo, long inProgress, long onHold,
            long completed, long rejected, long cancelled, long delayed) {}

    public record DashboardTaskResponse(Long id, Long groupId, String groupName, String title,
            Long projectId, String projectName, Long projectTopicId, String projectTopicTitle,
            String status, String priority, LocalDateTime dueAt, boolean delayed,
            LocalDateTime createdAt, LocalDateTime startAt, LocalDateTime completedAt,
            Long assigneeMemberId, String assigneeNickname) {
        public DashboardTaskResponse(Long id, Long groupId, String groupName, String title,
                String status, String priority, LocalDateTime dueAt, boolean delayed,
                LocalDateTime createdAt, LocalDateTime startAt, LocalDateTime completedAt,
                Long assigneeMemberId, String assigneeNickname) {
            this(id, groupId, groupName, title, null, null, null, null,
                    status, priority, dueAt, delayed, createdAt, startAt, completedAt,
                    assigneeMemberId, assigneeNickname);
        }
    }

    public record PersonalGroupSummary(Long groupId, String groupName, String groupType,
            long assignedCount, long completedCount, long activeCount, long delayedCount) {}

    public record PersonalDashboardResponse(Instant generatedAt, long todayDueCount,
            long delayedCount, long inProgressCount, long unreadNotificationCount,
            List<DashboardTaskResponse> priorityTasks, List<PersonalGroupSummary> groups,
            List<CalendarItemResponse> upcomingItems, List<NotificationResponse> unreadNotifications) {}

    public record MemberMetrics(Long memberId, Long userId, String nickname, String role,
            long assignedCount, long activeCount, long completedCount, long delayedCount,
            Integer onTimeRatePercent) {}

    public record GroupDashboardResponse(Instant generatedAt, Long groupId, String groupName,
            String timezone, String visibility, LocalDate periodFrom, LocalDate periodTo,
            long totalCount, StatusCounts statuses, Integer workflowProgressPercent,
            long periodCreatedCount, long periodCompletedCount, Integer periodCompletionRatePercent,
            long completedWithDueDateCount, long onTimeCompletedCount, Integer onTimeRatePercent,
            Long averageCompletionHours, List<MemberMetrics> members,
            List<DashboardTaskResponse> riskTasks, List<DashboardTaskResponse> periodTasks,
            List<DashboardTaskResponse> statusTasks, List<CalendarItemResponse> calendarItems) {}

    public record MemberReportResponse(Long groupId, String groupName, Long memberId,
            LocalDate periodFrom, LocalDate periodTo, List<DashboardTaskResponse> tasks) {}
}
