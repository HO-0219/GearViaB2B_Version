package com.teamproject.assistant.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.report.domain.AiWeeklyReportRevisionRepository;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.project.domain.Project;
import com.teamproject.project.domain.ProjectIssue;
import com.teamproject.project.domain.ProjectIssueRepository;
import com.teamproject.project.domain.ProjectRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAssistantContextService {
    private final GroupAuthorization authorization;
    private final TaskRepository tasks;
    private final AiWeeklyReportRevisionRepository reports;
    private final GroupMemberRepository members;
    private final ObjectMapper objectMapper;
    private final ProjectRepository projects;
    private final ProjectIssueRepository projectIssues;

    public AiAssistantContextService(GroupAuthorization authorization, TaskRepository tasks,
            AiWeeklyReportRevisionRepository reports, GroupMemberRepository members,
            ObjectMapper objectMapper, ProjectRepository projects,
            ProjectIssueRepository projectIssues) {
        this.authorization = authorization;
        this.tasks = tasks;
        this.reports = reports;
        this.members = members;
        this.objectMapper = objectMapper;
        this.projects = projects;
        this.projectIssues = projectIssues;
    }

    @Transactional(readOnly = true)
    public Context load(Long userId, Long groupId) {
        var member = authorization.requireActiveMember(groupId, userId);
        var group = member.getGroup();
        var context = new LinkedHashMap<String, Object>();
        context.put("now", LocalDateTime.now().toString());
        context.put("group", new GroupContext(group.getId(), group.getName(), group.getType().name(),
                group.getTimezone(), member.getRole().name()));
        var workspaces = members.findAllByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(
                userId, GroupMember.Status.ACTIVE);
        context.put("availableWorkspaces", workspaces.stream().map(value -> new WorkspaceContext(
                value.getGroup().getId(), value.getGroup().getName(), value.getGroup().getType().name(),
                value.getRole().name())).toList());
        var recentTasks = tasks.findByGroupIdOrderByUpdatedAtDescIdDesc(
                groupId, PageRequest.of(0, 30));
        context.put("recentTasks", recentTasks.stream().map(task -> new TaskContext(
                        task.getId(), task.getTitle(), task.getStatus().name(), task.getPriority().name(),
                        task.getProject() == null ? null : task.getProject().getId(),
                        task.getProject() == null ? null : task.getProject().getName(),
                        task.getProjectTopic() == null ? null : task.getProjectTopic().getId(),
                        task.getProjectTopic() == null ? null : task.getProjectTopic().getTitle(),
                        task.getDueAt(), task.getVersion())).toList());
        var activeProjects = projects.findAllByGroupIdOrderByUpdatedAtDescIdDesc(groupId).stream()
                .filter(project -> project.getStatus() != Project.Status.ARCHIVED).toList();
        context.put("projects", activeProjects.stream().map(project -> new ProjectContext(
                project.getId(), project.getName(), project.getStatus().name(),
                projectIssues.findAllByProjectIdAndArchivedAtIsNullOrderBySortOrderAscIdAsc(project.getId())
                        .stream().filter(topic -> topic.getLevel() == ProjectIssue.Level.MAJOR)
                        .map(topic -> new TopicContext(topic.getId(), topic.getTitle())).toList())).toList());
        var groupMembers = members.findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(
                groupId, GroupMember.Status.ACTIVE);
        context.put("members", groupMembers.stream().map(value -> new MemberContext(
                value.getId(), value.getUser().getNickname(), value.getRole().name())).toList());
        reports.findTopByGroupIdOrderByPeriodToExclusiveDescRevisionDesc(groupId).ifPresent(report -> {
            String analysis = report.getAnalysisJson();
            context.put("latestWeeklyReport", new ReportContext(report.getPeriodFrom(),
                    report.getPeriodToExclusive(), analysis.substring(0, Math.min(analysis.length(), 6000))));
        });
        try {
            return new Context(group, objectMapper.writeValueAsString(context),
                    workspaces.stream().map(value -> value.getGroup().getId()).collect(Collectors.toSet()),
                    recentTasks.stream().map(task -> task.getId()).collect(Collectors.toSet()),
                    groupMembers.stream().map(GroupMember::getId).collect(Collectors.toSet()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI assistant context serialization failed", exception);
        }
    }

    public record Context(com.teamproject.group.domain.Group group, String json,
            Set<Long> availableGroupIds, Set<Long> recentTaskIds, Set<Long> memberIds) {}
    private record GroupContext(Long id, String name, String type, String timezone, String currentUserRole) {}
    private record WorkspaceContext(Long id, String name, String type, String currentUserRole) {}
    private record MemberContext(Long id, String nickname, String role) {}
    private record TaskContext(Long id, String title, String status, String priority,
            Long projectId, String projectName, Long projectTopicId, String projectTopicTitle,
            LocalDateTime dueAt, long version) {}
    private record ProjectContext(Long id, String name, String status, List<TopicContext> topics) {}
    private record TopicContext(Long id, String title) {}
    private record ReportContext(java.time.LocalDate from, java.time.LocalDate toExclusive, String analysis) {}
}
