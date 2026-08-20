package com.teamproject.assistant.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.assistant.application.dto.AiAssistantDtos.ChatRequest;
import com.teamproject.assistant.application.dto.AiAssistantDtos.ChatResponse;
import com.teamproject.assistant.application.port.AiAssistantGateway;
import com.teamproject.assistant.application.port.AiAssistantGateway.TextDecision;
import com.teamproject.assistant.application.port.AiAssistantGateway.ToolDecision;
import com.teamproject.assistant.domain.AiAssistantAction;
import com.teamproject.assistant.domain.AiAssistantActionRepository;
import com.teamproject.assistant.domain.AiAssistantMessage;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiAssistantChatService {
    private static final Set<String> TOOLS = Set.of(
            "create_task", "create_group_invite_link", "approve_task", "add_task_checklist",
            "select_workspace", "create_task_comment", "send_group_notification");
    private final AiAssistantContextService contexts;
    private final AiAssistantGateway gateway;
    private final AiAssistantActionRepository actions;
    private final UserRepository users;
    private final ObjectMapper objectMapper;
    private final AiAssistantMessageStore messages;
    private final AiAssistantEntitlementService entitlement;

    public AiAssistantChatService(AiAssistantContextService contexts, AiAssistantGateway gateway,
            AiAssistantActionRepository actions, UserRepository users, ObjectMapper objectMapper,
            AiAssistantMessageStore messages, AiAssistantEntitlementService entitlement) {
        this.contexts = contexts;
        this.gateway = gateway;
        this.actions = actions;
        this.users = users;
        this.objectMapper = objectMapper;
        this.messages = messages;
        this.entitlement = entitlement;
    }

    public ChatResponse chat(Long userId, ChatRequest request) {
        entitlement.require(userId, request.groupId());
        var context = contexts.load(userId, request.groupId());
        var history = messages.modelContext(userId, request.groupId());
        messages.append(userId, context.group(), AiAssistantMessage.Role.USER, request.message(), null);
        var decision = gateway.decide(context.json(), history, request.message());
        if (decision instanceof TextDecision text) {
            String response = text.text() == null || text.text().isBlank()
                    ? "요청을 이해하지 못했습니다. 조금 더 구체적으로 말씀해 주세요."
                    : text.text().trim();
            messages.append(userId, context.group(), AiAssistantMessage.Role.ASSISTANT, response, null);
            return new ChatResponse(response, null, null, null, null);
        }
        ToolDecision tool = (ToolDecision) decision;
        if (!TOOLS.contains(tool.name())) invalidTool();
        JsonNode arguments = parse(tool.argumentsJson());
        validateScope(tool.name(), arguments, context);
        String summary = summary(tool.name(), arguments);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        var user = users.findById(userId).orElseThrow();
        var action = actions.save(new AiAssistantAction(user, context.group(), tool.name(),
                arguments.toString(), summary, expiresAt));
        String response = "아래 작업을 실행할까요? 내용을 확인해 주세요.";
        messages.append(userId, context.group(), AiAssistantMessage.Role.ASSISTANT, response, action);
        return new ChatResponse(response,
                action.getId(), tool.name(), summary, expiresAt);
    }

    private JsonNode parse(String value) {
        try {
            JsonNode node = objectMapper.readTree(value);
            if (!node.isObject()) invalidTool();
            return node;
        } catch (Exception exception) {
            throw new ApplicationException("AI_ASSISTANT_INVALID_ACTION", HttpStatus.BAD_GATEWAY,
                    "AI 비서가 올바르지 않은 작업을 제안했습니다. 다시 요청해 주세요.");
        }
    }

    private void validateScope(String tool, JsonNode arguments,
            AiAssistantContextService.Context context) {
        if (tool.equals("approve_task") || tool.equals("add_task_checklist")
                || tool.equals("create_task_comment")) {
            long taskId = arguments.path("taskId").asLong(0);
            if (taskId <= 0 || !context.recentTaskIds().contains(taskId)) invalidTool();
        }
        if (tool.equals("select_workspace")) {
            long groupId = arguments.path("groupId").asLong(0);
            if (groupId <= 0 || !context.availableGroupIds().contains(groupId)) invalidTool();
        }
        if (tool.equals("create_task_comment")) {
            validateMemberIds(arguments.path("mentionedMemberIds"), context);
        }
        if (tool.equals("send_group_notification")) {
            validateMemberIds(arguments.path("recipientMemberIds"), context);
        }
    }

    private void validateMemberIds(JsonNode node, AiAssistantContextService.Context context) {
        if (node.isNull() || node.isMissingNode()) return;
        if (!node.isArray() || node.isEmpty() || node.size() > 20) invalidTool();
        node.forEach(value -> {
            if (!value.canConvertToLong() || !context.memberIds().contains(value.asLong())) invalidTool();
        });
    }

    private String summary(String tool, JsonNode args) {
        return switch (tool) {
            case "create_task" -> "업무 생성: " + clipped(args.path("title").asText(), 120);
            case "create_group_invite_link" -> "새 그룹 초대 링크 생성";
            case "approve_task" -> "업무 #" + args.path("taskId").asLong() + " 승인";
            case "add_task_checklist" -> "업무 #" + args.path("taskId").asLong()
                    + "에 체크리스트 " + args.path("items").size() + "개 추가";
            case "select_workspace" -> "작업공간 #" + args.path("groupId").asLong() + " 선택";
            case "create_task_comment" -> "업무 #" + args.path("taskId").asLong() + "에 댓글 작성";
            case "send_group_notification" -> "그룹 멤버 "
                    + args.path("recipientMemberIds").size() + "명에게 알림 전송";
            default -> invalidSummary();
        };
    }

    private String clipped(String value, int max) {
        if (value == null || value.isBlank()) invalidTool();
        return value.length() <= max ? value : value.substring(0, max);
    }

    private JsonNode invalidTool() {
        throw new ApplicationException("AI_ASSISTANT_INVALID_ACTION", HttpStatus.BAD_GATEWAY,
                "AI 비서가 올바르지 않은 작업을 제안했습니다. 다시 요청해 주세요.");
    }

    private String invalidSummary() {
        invalidTool();
        throw new IllegalStateException("unreachable");
    }
}
