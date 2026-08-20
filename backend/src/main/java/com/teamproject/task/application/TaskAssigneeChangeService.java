package com.teamproject.task.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.task.application.dto.TaskAssigneeChangeDtos.CreateRequest;
import com.teamproject.task.application.dto.TaskAssigneeChangeDtos.DecisionRequest;
import com.teamproject.task.application.dto.TaskAssigneeChangeDtos.Response;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskActivityEvent;
import com.teamproject.task.domain.TaskAssigneeChangeRequest;
import com.teamproject.task.domain.TaskAssigneeChangeRequestRepository;
import com.teamproject.task.domain.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class TaskAssigneeChangeService {
    private final GroupAuthorization authorization;
    private final GroupMemberRepository members;
    private final TaskRepository tasks;
    private final TaskAssigneeChangeRequestRepository requests;
    private final TaskActivityRecorder activity;
    private final NotificationService notifications;

    public TaskAssigneeChangeService(GroupAuthorization authorization, GroupMemberRepository members,
            TaskRepository tasks, TaskAssigneeChangeRequestRepository requests,
            TaskActivityRecorder activity, NotificationService notifications) {
        this.authorization = authorization; this.members = members; this.tasks = tasks;
        this.requests = requests; this.activity = activity; this.notifications = notifications;
    }

    @Transactional
    public Response create(Long userId, Long taskId, CreateRequest input) {
        Task task = task(taskId);
        GroupMember actor = authorization.requireActiveMember(task.getGroup().getId(), userId);
        if (task.getGroup().getType() != Group.Type.TEAM || terminal(task.getStatus())
                || task.getStatus() == Task.Status.REQUESTED) {
            throw conflict("ASSIGNEE_CHANGE_UNAVAILABLE", "승인되어 진행 중인 팀 업무만 담당자 변경을 요청할 수 있습니다.");
        }
        if (task.getAssignee() == null) {
            throw conflict("ASSIGNEE_CHANGE_NOT_REQUIRED", "담당자가 없는 업무는 팀장이 바로 지정할 수 있습니다.");
        }
        if (!actor.getId().equals(task.getRequester().getId())
                && !actor.getId().equals(task.getAssignee().getId())
                && actor.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("ASSIGNEE_CHANGE_FORBIDDEN", HttpStatus.FORBIDDEN,
                    "업무 요청자, 현재 담당자 또는 팀장만 변경을 요청할 수 있습니다.");
        }
        GroupMember proposed = authorization.requireActiveMemberById(task.getGroup().getId(), input.assigneeMemberId());
        if (task.getAssignee().getId().equals(proposed.getId())) {
            throw conflict("ASSIGNEE_UNCHANGED", "현재 담당자와 다른 팀원을 선택해 주세요.");
        }
        if (requests.existsByTaskIdAndStatus(taskId, TaskAssigneeChangeRequest.Status.PENDING)) {
            throw conflict("ASSIGNEE_CHANGE_PENDING", "이미 팀장 승인을 기다리는 담당자 변경 요청이 있습니다.");
        }
        TaskAssigneeChangeRequest saved = requests.save(new TaskAssigneeChangeRequest(
                task, actor, proposed, blank(input.reason())));
        List<GroupMember> leaders = members.findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(
                task.getGroup().getId(), GroupMember.Status.ACTIVE).stream()
                .filter(member -> member.getRole() == GroupMember.Role.LEADER).toList();
        notifications.assistantMessage(actor, leaders, "ASSIGNEE_CHANGE_REQUEST:" + saved.getId(),
                "담당자 변경 승인 요청", "'" + task.getTitle() + "' 담당자를 "
                        + proposed.getUser().getNickname() + "님으로 변경하는 요청이 도착했습니다.");
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<Response> list(Long userId, Long groupId) {
        authorization.requireActiveMember(groupId, userId);
        return requests.findAllByTask_Group_IdOrderByCreatedAtDesc(groupId).stream().map(this::response).toList();
    }

    @Transactional
    public Response decide(Long userId, Long requestId, DecisionRequest input) {
        TaskAssigneeChangeRequest request = requests.findById(requestId).orElseThrow(() ->
                new ApplicationException("ASSIGNEE_CHANGE_NOT_FOUND", HttpStatus.NOT_FOUND, "담당자 변경 요청을 찾을 수 없습니다."));
        GroupMember leader = authorization.requireLeader(request.getTask().getGroup().getId(), userId);
        if (request.getStatus() != TaskAssigneeChangeRequest.Status.PENDING) {
            throw conflict("ASSIGNEE_CHANGE_REVIEWED", "이미 처리된 담당자 변경 요청입니다.");
        }
        if (request.getVersion() != input.expectedVersion()) {
            throw conflict("VERSION_CONFLICT", "다른 사용자가 먼저 처리했습니다. 새로고침 후 다시 시도해 주세요.");
        }
        boolean approve = switch (input.decision().trim().toUpperCase()) {
            case "APPROVE" -> true;
            case "REJECT" -> false;
            default -> throw new ApplicationException("ASSIGNEE_CHANGE_DECISION_INVALID", HttpStatus.BAD_REQUEST,
                    "승인 또는 반려를 선택해 주세요.");
        };
        Task task = request.getTask();
        if (approve) {
            if (terminal(task.getStatus())) throw conflict("ASSIGNEE_CHANGE_UNAVAILABLE", "종료된 업무의 담당자는 변경할 수 없습니다.");
            task.assign(request.getProposedAssignee());
            tasks.flush();
            activity.record(task, leader, TaskActivityEvent.Type.ASSIGNEE_CHANGED);
            notifications.taskAssigned(task, leader, request.getProposedAssignee());
        }
        request.review(leader, approve, blank(input.note()));
        requests.flush();
        notifications.assistantMessage(leader, List.of(request.getRequestedBy()),
                "ASSIGNEE_CHANGE_REVIEW:" + request.getId() + ":" + request.getVersion(),
                approve ? "담당자 변경 승인" : "담당자 변경 반려",
                "'" + task.getTitle() + "' 담당자 변경 요청이 " + (approve ? "승인" : "반려") + "되었습니다.");
        return response(request);
    }

    private Task task(Long id) {
        return tasks.findById(id).orElseThrow(() -> new ApplicationException("TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다."));
    }
    private boolean terminal(Task.Status status) { return status == Task.Status.COMPLETED || status == Task.Status.REJECTED || status == Task.Status.CANCELLED; }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ApplicationException conflict(String code, String message) { return new ApplicationException(code, HttpStatus.CONFLICT, message); }
    private Response response(TaskAssigneeChangeRequest value) {
        return new Response(value.getId(), value.getTask().getId(), value.getTask().getTitle(),
                value.getRequestedBy().getId(), value.getRequestedBy().getUser().getNickname(),
                value.getProposedAssignee().getId(), value.getProposedAssignee().getUser().getNickname(),
                value.getStatus().name(), value.getReason(), value.getReviewedBy() == null ? null : value.getReviewedBy().getId(),
                value.getReviewNote(), value.getCreatedAt(), value.getReviewedAt(), value.getVersion());
    }
}
