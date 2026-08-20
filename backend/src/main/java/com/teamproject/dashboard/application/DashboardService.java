package com.teamproject.dashboard.application;

import com.teamproject.calendar.application.CalendarService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.dashboard.application.dto.DashboardDtos.*;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private static final Set<Task.Status> TERMINAL = Set.of(
            Task.Status.COMPLETED, Task.Status.REJECTED, Task.Status.CANCELLED);
    private final TaskRepository tasks;
    private final GroupMemberRepository members;
    private final GroupAuthorization authorization;
    private final CalendarService calendars;
    private final NotificationService notifications;

    public DashboardService(TaskRepository tasks, GroupMemberRepository members,
            GroupAuthorization authorization, CalendarService calendars,
            NotificationService notifications) {
        this.tasks = tasks;
        this.members = members;
        this.authorization = authorization;
        this.calendars = calendars;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public PersonalDashboardResponse personal(Long userId) {
        List<Task> assigned = tasks.findAllByAssigneeUserIdAndAssigneeStatus(
                userId, GroupMember.Status.ACTIVE);
        Instant now = Instant.now();
        long todayDue = assigned.stream().filter(task -> {
            if (task.getDueAt() == null || TERMINAL.contains(task.getStatus())) return false;
            return task.getDueAt().toLocalDate().equals(LocalDate.now(zone(task.getGroup())));
        }).count();
        long delayed = assigned.stream().filter(this::delayed).count();
        long inProgress = assigned.stream().filter(task -> task.getStatus() == Task.Status.IN_PROGRESS).count();
        List<DashboardTaskResponse> priorityTasks = assigned.stream()
                .filter(task -> !TERMINAL.contains(task.getStatus()))
                .sorted(Comparator.comparingInt((Task task) -> delayed(task) ? 0 : 1)
                        .thenComparing(Task::getDueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing((Task task) -> priorityRank(task.getPriority()), Comparator.reverseOrder())
                        .thenComparing(Task::getId)).limit(10).map(this::taskResponse).toList();
        List<PersonalGroupSummary> groupSummaries = assigned.stream()
                .collect(Collectors.groupingBy(task -> task.getGroup().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream().map(groupTasks -> {
                    Group group = groupTasks.get(0).getGroup();
                    return new PersonalGroupSummary(group.getId(), group.getName(), group.getType().name(),
                            groupTasks.size(), count(groupTasks, Task.Status.COMPLETED),
                            groupTasks.stream().filter(task -> !TERMINAL.contains(task.getStatus())).count(),
                            groupTasks.stream().filter(this::delayed).count());
                }).sorted(Comparator.comparing(PersonalGroupSummary::groupType)
                        .thenComparing(PersonalGroupSummary::groupName)).toList();
        LocalDate from = LocalDate.now();
        var notificationPage = notifications.unread(userId, 5);
        return new PersonalDashboardResponse(now, todayDue, delayed, inProgress,
                notificationPage.unreadCount(), priorityTasks, groupSummaries,
                calendars.list(userId, null, from, from.plusDays(8)).items().stream().limit(10).toList(),
                notificationPage.items());
    }

    @Transactional(readOnly = true)
    public GroupDashboardResponse group(Long userId, Long groupId, LocalDate from, LocalDate to) {
        GroupMember viewer = authorization.requireActiveMember(groupId, userId);
        Group group = viewer.getGroup();
        if (group.getDashboardVisibility() == Group.DashboardVisibility.LEADER_ONLY
                && viewer.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("DASHBOARD_FORBIDDEN", HttpStatus.FORBIDDEN,
                    "그룹 대시보드를 조회할 권한이 없습니다.");
        }
        LocalDate today = LocalDate.now(zone(group));
        LocalDate periodFrom = from == null ? today.minusDays(29) : from;
        LocalDate periodTo = to == null ? today.plusDays(1) : to;
        validateRange(periodFrom, periodTo);
        List<Task> groupTasks = tasks.findAllByGroupIdOrderByCreatedAtDesc(groupId);
        LocalDateTime periodStart = periodFrom.atStartOfDay();
        LocalDateTime periodEnd = periodTo.atStartOfDay();
        List<Task> periodTasks = groupTasks.stream().filter(task -> occursInPeriod(task, periodStart, periodEnd)).toList();
        var statusTasks = new LinkedHashMap<Long, Task>();
        groupTasks.stream().filter(task -> task.getStatus() == Task.Status.REQUESTED)
                .forEach(task -> statusTasks.put(task.getId(), task));
        periodTasks.forEach(task -> statusTasks.put(task.getId(), task));
        StatusCounts statuses = statuses(groupTasks, periodTasks);
        List<Task> workflowTasks = groupTasks.stream()
                .filter(task -> task.getStatus() == Task.Status.REQUESTED || periodTasks.contains(task))
                .filter(task -> task.getStatus() != Task.Status.REJECTED && task.getStatus() != Task.Status.CANCELLED)
                .toList();
        Integer progress = workflowTasks.isEmpty() ? null : percent(workflowTasks.stream()
                .mapToLong(task -> progressWeight(task.getStatus())).sum(), workflowTasks.size() * 100L);
        List<Task> createdInPeriod = groupTasks.stream().filter(task -> !task.getCreatedAt().isBefore(periodStart)
                && task.getCreatedAt().isBefore(periodEnd)).toList();
        long periodCompleted = createdInPeriod.stream().filter(task -> task.getStatus() == Task.Status.COMPLETED).count();
        List<Task> completedWithDue = periodTasks.stream().filter(task -> task.getStatus() == Task.Status.COMPLETED
                && task.getDueAt() != null && task.getCompletedAt() != null).toList();
        long onTime = completedWithDue.stream().filter(task -> !task.getCompletedAt().isAfter(task.getDueAt())).count();
        List<Task> completed = periodTasks.stream().filter(task -> task.getStatus() == Task.Status.COMPLETED
                && task.getCompletedAt() != null).toList();
        Long averageHours = completed.isEmpty() ? null : Math.round(completed.stream()
                .mapToLong(task -> Duration.between(task.getCreatedAt(), task.getCompletedAt()).toMinutes())
                .average().orElse(0) / 60.0);
        Map<Long, List<Task>> byAssignee = periodTasks.stream().filter(task -> task.getAssignee() != null)
                .collect(Collectors.groupingBy(task -> task.getAssignee().getId()));
        List<MemberMetrics> memberMetrics = members.findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(
                groupId, GroupMember.Status.ACTIVE).stream().map(member -> memberMetrics(member,
                        byAssignee.getOrDefault(member.getId(), List.of()))).toList();
        List<DashboardTaskResponse> risks = periodTasks.stream()
                .filter(task -> !TERMINAL.contains(task.getStatus()))
                .filter(task -> delayed(task) || task.getPriority() == Task.Priority.HIGH
                        || task.getPriority() == Task.Priority.URGENT)
                .sorted(Comparator.comparingInt((Task task) -> delayed(task) ? 0 : 1)
                        .thenComparing((Task task) -> priorityRank(task.getPriority()), Comparator.reverseOrder())
                        .thenComparing(Task::getDueAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(10).map(this::taskResponse).toList();
        return new GroupDashboardResponse(Instant.now(), groupId, group.getName(), group.getTimezone(),
                group.getDashboardVisibility().name(), periodFrom, periodTo, groupTasks.size(), statuses,
                progress, createdInPeriod.size(), periodCompleted,
                createdInPeriod.isEmpty() ? null : percent(periodCompleted, createdInPeriod.size()),
                completedWithDue.size(), onTime,
                completedWithDue.isEmpty() ? null : percent(onTime, completedWithDue.size()),
                averageHours, memberMetrics, risks, periodTasks.stream().map(this::taskResponse).toList(),
                statusTasks.values().stream().map(this::taskResponse).toList(),
                calendars.list(userId, groupId, periodFrom, periodTo).items());
    }

    @Transactional(readOnly = true)
    public MemberReportResponse memberReport(Long userId, Long groupId, LocalDate from, LocalDate to) {
        GroupMember viewer = authorization.requireActiveMember(groupId, userId);
        Group group = viewer.getGroup();
        LocalDate today = LocalDate.now(zone(group));
        LocalDate periodFrom = from == null ? today.minusDays(6) : from;
        LocalDate periodTo = to == null ? today.plusDays(1) : to;
        validateRange(periodFrom, periodTo);
        LocalDateTime periodStart = periodFrom.atStartOfDay();
        LocalDateTime periodEnd = periodTo.atStartOfDay();
        List<DashboardTaskResponse> assigned = tasks.findAllByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .filter(task -> task.getAssignee() != null && task.getAssignee().getId().equals(viewer.getId()))
                .filter(task -> occursInPeriod(task, periodStart, periodEnd))
                .map(this::taskResponse).toList();
        return new MemberReportResponse(groupId, group.getName(), viewer.getId(),
                periodFrom, periodTo, assigned);
    }

    private StatusCounts statuses(List<Task> all, List<Task> period) {
        return new StatusCounts(count(all, Task.Status.REQUESTED), count(period, Task.Status.TODO),
                count(period, Task.Status.IN_PROGRESS), count(period, Task.Status.ON_HOLD),
                count(period, Task.Status.COMPLETED), count(period, Task.Status.REJECTED),
                count(period, Task.Status.CANCELLED), period.stream().filter(this::delayed).count());
    }
    private MemberMetrics memberMetrics(GroupMember member, List<Task> assigned) {
        List<Task> withDue = assigned.stream().filter(task -> task.getStatus() == Task.Status.COMPLETED
                && task.getDueAt() != null && task.getCompletedAt() != null).toList();
        long onTime = withDue.stream().filter(task -> !task.getCompletedAt().isAfter(task.getDueAt())).count();
        return new MemberMetrics(member.getId(), member.getUser().getId(), member.getUser().getNickname(),
                member.getRole().name(), assigned.size(),
                assigned.stream().filter(task -> !TERMINAL.contains(task.getStatus())).count(),
                count(assigned, Task.Status.COMPLETED), assigned.stream().filter(this::delayed).count(),
                withDue.isEmpty() ? null : percent(onTime, withDue.size()));
    }
    private DashboardTaskResponse taskResponse(Task task) {
        return new DashboardTaskResponse(task.getId(), task.getGroup().getId(), task.getGroup().getName(),
                task.getTitle(),
                task.getProject() == null ? null : task.getProject().getId(),
                task.getProject() == null ? null : task.getProject().getName(),
                task.getProjectTopic() == null ? null : task.getProjectTopic().getId(),
                task.getProjectTopic() == null ? null : task.getProjectTopic().getTitle(),
                task.getStatus().name(), task.getPriority().name(), task.getDueAt(), delayed(task),
                task.getCreatedAt(), task.getStartAt(), task.getCompletedAt(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getAssignee() == null ? null : task.getAssignee().getUser().getNickname());
    }
    private boolean occursInPeriod(Task task, LocalDateTime from, LocalDateTime to) {
        return inRange(task.getCreatedAt(), from, to) || inRange(task.getStartAt(), from, to)
                || inRange(task.getDueAt(), from, to) || inRange(task.getCompletedAt(), from, to)
                || inRange(task.getUpdatedAt(), from, to);
    }
    private boolean inRange(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        return value != null && !value.isBefore(from) && value.isBefore(to);
    }
    private boolean delayed(Task task) {
        return task.isDelayed(LocalDateTime.now(zone(task.getGroup())));
    }
    private ZoneId zone(Group group) { return ZoneId.of(group.getTimezone()); }
    private long count(List<Task> values, Task.Status status) {
        return values.stream().filter(task -> task.getStatus() == status).count();
    }
    private int percent(long numerator, long denominator) {
        return (int) Math.round(numerator * 100.0 / denominator);
    }
    private int priorityRank(Task.Priority priority) {
        return switch (priority) { case LOW -> 0; case NORMAL -> 1; case HIGH -> 2; case URGENT -> 3; };
    }
    private int progressWeight(Task.Status status) {
        return switch (status) {
            case REQUESTED -> 0;
            case TODO -> 25;
            case IN_PROGRESS, ON_HOLD -> 50;
            case COMPLETED -> 100;
            case REJECTED, CANCELLED -> 0;
        };
    }
    private void validateRange(LocalDate from, LocalDate to) {
        if (!to.isAfter(from) || from.plusDays(366).isBefore(to)) {
            throw new ApplicationException("DASHBOARD_RANGE_INVALID", HttpStatus.BAD_REQUEST,
                    "대시보드 기간은 시작일 이후부터 최대 366일까지 입력해 주세요.");
        }
    }
}
