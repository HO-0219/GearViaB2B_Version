package com.teamproject.assistant.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.assistant.application.dto.AiAssistantDtos.ActionResponse;
import com.teamproject.assistant.domain.AiAssistantAction;
import com.teamproject.assistant.domain.AiAssistantActionRepository;
import com.teamproject.comment.application.TaskCommentService;
import com.teamproject.comment.application.dto.CommentDtos.CreateCommentRequest;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupInvitationService;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.task.application.TaskChecklistService;
import com.teamproject.task.application.TaskService;
import com.teamproject.task.application.dto.ChecklistDtos.CreateChecklistItemRequest;
import com.teamproject.task.application.dto.TaskDtos.CreateTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.TransitionTaskRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAssistantActionService {
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private final AiAssistantActionRepository actions;
    private final TaskService tasks;
    private final TaskChecklistService checklists;
    private final GroupInvitationService invitations;
    private final TaskCommentService comments;
    private final GroupAuthorization authorization;
    private final GroupMemberRepository members;
    private final NotificationService notifications;
    private final ObjectMapper objectMapper;
    private final AiAssistantEntitlementService entitlement;

    public AiAssistantActionService(AiAssistantActionRepository actions, TaskService tasks,
            TaskChecklistService checklists, GroupInvitationService invitations,
            TaskCommentService comments, GroupAuthorization authorization,
            GroupMemberRepository members, NotificationService notifications,
            ObjectMapper objectMapper, AiAssistantEntitlementService entitlement) {
        this.actions = actions;
        this.tasks = tasks;
        this.checklists = checklists;
        this.invitations = invitations;
        this.comments = comments;
        this.authorization = authorization;
        this.members = members;
        this.notifications = notifications;
        this.objectMapper = objectMapper;
        this.entitlement = entitlement;
    }

    @Transactional
    public ActionResponse confirm(Long userId, Long actionId) {
        AiAssistantAction action = action(userId, actionId);
        entitlement.require(userId, action.getGroup().getId());
        if (action.getStatus() == AiAssistantAction.Status.COMPLETED) {
            return new ActionResponse(actionId, "COMPLETED", "이미 실행한 작업입니다.", null, null, null);
        }
        if (action.getStatus() != AiAssistantAction.Status.PENDING) {
            return new ActionResponse(actionId, action.getStatus().name(), "실행할 수 없는 작업입니다.", null, null, null);
        }
        if (action.isExpiredAt(LocalDateTime.now())) {
            action.expire();
            return new ActionResponse(actionId, "EXPIRED", "확인 시간이 만료되었습니다. 다시 요청해 주세요.", null, null, null);
        }

        ActionResponse result = execute(userId, action);
        action.complete(LocalDateTime.now());
        return result;
    }

    @Transactional
    public ActionResponse cancel(Long userId, Long actionId) {
        AiAssistantAction action = action(userId, actionId);
        action.cancel();
        return new ActionResponse(actionId, action.getStatus().name(), "작업을 취소했습니다.", null, null, null);
    }

    private ActionResponse execute(Long userId, AiAssistantAction action) {
        try {
            return switch (action.getToolName()) {
                case "create_task" -> createTask(userId, action);
                case "create_group_invite_link" -> createInviteLink(userId, action);
                case "approve_task" -> approveTask(userId, action);
                case "add_task_checklist" -> addChecklist(userId, action);
                case "select_workspace" -> selectWorkspace(userId, action);
                case "create_task_comment" -> createComment(userId, action);
                case "send_group_notification" -> sendNotification(userId, action);
                default -> throw invalidAction();
            };
        } catch (ApplicationException exception) {
            if (exception.status() == HttpStatus.FORBIDDEN
                    || exception.code().equals("GROUP_NOT_FOUND")
                    || exception.code().equals("GROUP_MEMBER_NOT_FOUND")) {
                throw unauthorizedAction();
            }
            throw exception;
        } catch (Exception exception) {
            throw invalidAction();
        }
    }

    private ActionResponse createTask(Long userId, AiAssistantAction action) throws Exception {
        CreateTaskArgs args = objectMapper.readValue(action.getArgumentsJson(), CreateTaskArgs.class);
        String title = required(args.title(), 120);
        String description = optional(args.description(), 5000);
        String priority = args.priority() == null ? "NORMAL" : args.priority().toUpperCase();
        if (!PRIORITIES.contains(priority)) throw invalidAction();
        LocalDateTime dueAt = args.dueAt() == null || args.dueAt().isBlank()
                ? null : LocalDateTime.parse(args.dueAt());
        List<String> items = validatedItems(args.checklistItems());
        var task = tasks.create(userId, action.getGroup().getId(),
                new CreateTaskRequest(title, description, priority, dueAt,
                        args.projectId(), args.projectTopicId(), items));
        return new ActionResponse(action.getId(), "COMPLETED",
                "'" + task.title() + "' 업무를 만들었습니다.", "/tasks/" + task.id(), null, null);
    }

    private ActionResponse createInviteLink(Long userId, AiAssistantAction action) {
        var link = invitations.createLink(userId, action.getGroup().getId());
        return new ActionResponse(action.getId(), "COMPLETED", "새 초대 링크를 만들었습니다.",
                "/groups/" + action.getGroup().getId() + "/members", link.url(), null);
    }

    private ActionResponse approveTask(Long userId, AiAssistantAction action) throws Exception {
        TaskIdArgs args = objectMapper.readValue(action.getArgumentsJson(), TaskIdArgs.class);
        var current = tasks.get(userId, args.taskId());
        requireSameGroup(action, current.groupId());
        var task = tasks.transition(userId, args.taskId(),
                new TransitionTaskRequest("ACCEPT", null, current.version()));
        return new ActionResponse(action.getId(), "COMPLETED",
                "'" + task.title() + "' 업무를 승인했습니다.", "/tasks/" + task.id(), null, null);
    }

    private ActionResponse addChecklist(Long userId, AiAssistantAction action) throws Exception {
        ChecklistArgs args = objectMapper.readValue(action.getArgumentsJson(), ChecklistArgs.class);
        var task = tasks.get(userId, args.taskId());
        requireSameGroup(action, task.groupId());
        List<String> items = validatedItems(args.items());
        if (items == null || items.isEmpty()) throw invalidAction();
        for (String item : items) {
            checklists.create(userId, args.taskId(), new CreateChecklistItemRequest(item, null));
        }
        return new ActionResponse(action.getId(), "COMPLETED",
                "'" + task.title() + "' 업무에 체크리스트 " + items.size() + "개를 추가했습니다.",
                "/tasks/" + task.id(), null, null);
    }

    private ActionResponse selectWorkspace(Long userId, AiAssistantAction action) throws Exception {
        GroupIdArgs args = objectMapper.readValue(action.getArgumentsJson(), GroupIdArgs.class);
        var member = authorization.requireActiveMember(args.groupId(), userId);
        return new ActionResponse(action.getId(), "COMPLETED",
                "작업공간을 '" + member.getGroup().getName() + "'(으)로 변경했습니다.",
                null, null, member.getGroup().getId());
    }

    private ActionResponse createComment(Long userId, AiAssistantAction action) throws Exception {
        CommentArgs args = objectMapper.readValue(action.getArgumentsJson(), CommentArgs.class);
        var task = tasks.get(userId, args.taskId());
        requireSameGroup(action, task.groupId());
        String content = required(args.content(), 2000);
        List<Long> mentionedMemberIds = validatedMemberIds(action, args.mentionedMemberIds());
        comments.create(userId, args.taskId(), new CreateCommentRequest(content, mentionedMemberIds));
        return new ActionResponse(action.getId(), "COMPLETED",
                "'" + task.title() + "' 업무에 댓글을 작성했습니다.",
                "/tasks/" + task.id(), null, null);
    }

    private ActionResponse sendNotification(Long userId, AiAssistantAction action) throws Exception {
        NotificationArgs args = objectMapper.readValue(action.getArgumentsJson(), NotificationArgs.class);
        GroupMember actor = authorization.requireActiveMember(action.getGroup().getId(), userId);
        List<Long> ids = validatedMemberIds(action, args.recipientMemberIds());
        if (ids == null || ids.isEmpty()) throw invalidAction();
        var recipients = ids.stream().map(id -> authorization.requireActiveMemberById(
                action.getGroup().getId(), id)).toList();
        notifications.assistantMessage(actor, recipients,
                "AI_ASSISTANT_MESSAGE:" + action.getId(), required(args.title(), 160),
                required(args.message(), 500));
        return new ActionResponse(action.getId(), "COMPLETED",
                "그룹 멤버에게 알림을 보냈습니다.", "/notifications", null, null);
    }

    private List<Long> validatedMemberIds(AiAssistantAction action, List<Long> values) {
        if (values == null) return null;
        if (values.size() > 20 || values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw invalidAction();
        }
        return values.stream().distinct().peek(value -> members.findByIdAndGroupIdAndStatus(
                value, action.getGroup().getId(), GroupMember.Status.ACTIVE).orElseThrow(this::invalidAction))
                .toList();
    }

    private AiAssistantAction action(Long userId, Long actionId) {
        return actions.findByIdAndUserIdForUpdate(actionId, userId).orElseThrow(() ->
                new ApplicationException("AI_ASSISTANT_ACTION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "AI 비서 작업을 찾을 수 없습니다."));
    }

    private void requireSameGroup(AiAssistantAction action, Long groupId) {
        if (!action.getGroup().getId().equals(groupId)) throw invalidAction();
    }
    private String required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw invalidAction();
        return value.trim();
    }
    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > max) throw invalidAction();
        return value.trim();
    }
    private List<String> validatedItems(List<String> values) {
        if (values == null) return null;
        if (values.size() > 30) throw invalidAction();
        return values.stream().map(value -> required(value, 300)).toList();
    }
    private ApplicationException invalidAction() {
        return new ApplicationException("AI_ASSISTANT_INVALID_ACTION", HttpStatus.BAD_REQUEST,
                "AI 비서 작업 내용이 올바르지 않습니다. 다시 요청해 주세요.");
    }

    private ApplicationException unauthorizedAction() {
        return new ApplicationException("AI_ASSISTANT_ACTION_FORBIDDEN", HttpStatus.FORBIDDEN,
                "승인되지 않은 내용입니다.");
    }

    private record CreateTaskArgs(String title, String description, String priority,
            String dueAt, Long projectId, Long projectTopicId, List<String> checklistItems) {}
    private record TaskIdArgs(Long taskId) {}
    private record ChecklistArgs(Long taskId, List<String> items) {}
    private record GroupIdArgs(Long groupId) {}
    private record CommentArgs(Long taskId, String content, List<Long> mentionedMemberIds) {}
    private record NotificationArgs(List<Long> recipientMemberIds, String title, String message) {}
}
