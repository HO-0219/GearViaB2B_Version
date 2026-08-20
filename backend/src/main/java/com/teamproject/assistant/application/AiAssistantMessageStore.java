package com.teamproject.assistant.application;

import com.teamproject.assistant.application.dto.AiAssistantDtos.MessageResponse;
import com.teamproject.assistant.application.port.AiAssistantGateway.ChatMessage;
import com.teamproject.assistant.domain.AiAssistantAction;
import com.teamproject.assistant.domain.AiAssistantMessage;
import com.teamproject.assistant.domain.AiAssistantMessageRepository;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import com.teamproject.user.domain.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAssistantMessageStore {
    private static final int DISPLAY_LIMIT = 100;
    private static final int MODEL_CONTEXT_LIMIT = 20;
    private final AiAssistantMessageRepository messages;
    private final GroupAuthorization authorization;
    private final UserRepository users;
    private final AiAssistantEntitlementService entitlement;

    public AiAssistantMessageStore(AiAssistantMessageRepository messages,
            GroupAuthorization authorization, UserRepository users,
            AiAssistantEntitlementService entitlement) {
        this.messages = messages;
        this.authorization = authorization;
        this.users = users;
        this.entitlement = entitlement;
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> list(Long userId, Long groupId) {
        entitlement.require(userId, groupId);
        authorization.requireActiveMember(groupId, userId);
        return chronological(messages.findByUserIdAndGroupIdOrderByIdDesc(
                userId, groupId, PageRequest.of(0, DISPLAY_LIMIT))).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> modelContext(Long userId, Long groupId) {
        entitlement.require(userId, groupId);
        authorization.requireActiveMember(groupId, userId);
        return chronological(messages.findByUserIdAndGroupIdOrderByIdDesc(
                userId, groupId, PageRequest.of(0, MODEL_CONTEXT_LIMIT))).stream()
                .map(message -> new ChatMessage(message.getRole().name(), message.getContent()))
                .toList();
    }

    @Transactional
    public void append(Long userId, Group group, AiAssistantMessage.Role role,
            String content, AiAssistantAction action) {
        var user = users.findById(userId).orElseThrow();
        messages.save(new AiAssistantMessage(user, group, role, content, action));
    }

    private List<AiAssistantMessage> chronological(List<AiAssistantMessage> newestFirst) {
        var result = new ArrayList<>(newestFirst);
        java.util.Collections.reverse(result);
        return result;
    }

    private MessageResponse response(AiAssistantMessage message) {
        AiAssistantAction action = message.getAction();
        return new MessageResponse(message.getId(), message.getRole().name().toLowerCase(),
                message.getContent(), action == null ? null : action.getId(),
                action == null ? null : action.getToolName(),
                action == null ? null : action.getSummary(),
                action == null ? null : action.getExpiresAt(),
                action == null ? null : action.getStatus().name(), message.getCreatedAt());
    }
}
