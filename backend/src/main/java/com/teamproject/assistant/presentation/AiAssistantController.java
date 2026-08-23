package com.teamproject.assistant.presentation;

import com.teamproject.assistant.application.AiAssistantActionService;
import com.teamproject.assistant.application.AiAssistantChatService;
import com.teamproject.assistant.application.AiAssistantMessageStore;
import com.teamproject.assistant.application.AiDocumentIndexService;
import com.teamproject.assistant.application.dto.AiAssistantDtos.ActionResponse;
import com.teamproject.assistant.application.dto.AiAssistantDtos.ChatRequest;
import com.teamproject.assistant.application.dto.AiAssistantDtos.ChatResponse;
import com.teamproject.assistant.application.dto.AiAssistantDtos.IndexResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assistant")
public class AiAssistantController {
    private final AiAssistantChatService chat;
    private final AiAssistantActionService actions;
    private final AiAssistantMessageStore messages;
    private final AiDocumentIndexService index;

    public AiAssistantController(AiAssistantChatService chat, AiAssistantActionService actions,
            AiAssistantMessageStore messages, AiDocumentIndexService index) {
        this.chat = chat;
        this.actions = actions;
        this.messages = messages;
        this.index = index;
    }

    @PostMapping("/reindex")
    IndexResponse reindex(Authentication authentication, @RequestParam Long groupId) {
        return index.reindex((Long) authentication.getPrincipal(), groupId);
    }

    @GetMapping("/messages")
    List<com.teamproject.assistant.application.dto.AiAssistantDtos.MessageResponse> messages(
            Authentication authentication, @RequestParam Long groupId) {
        return messages.list((Long) authentication.getPrincipal(), groupId);
    }

    @PostMapping("/messages")
    ChatResponse message(Authentication authentication, @Valid @RequestBody ChatRequest request) {
        return chat.chat((Long) authentication.getPrincipal(), request);
    }

    @PostMapping("/actions/{actionId}/confirm")
    ActionResponse confirm(Authentication authentication, @PathVariable Long actionId) {
        return actions.confirm((Long) authentication.getPrincipal(), actionId);
    }

    @PostMapping("/actions/{actionId}/cancel")
    ActionResponse cancel(Authentication authentication, @PathVariable Long actionId) {
        return actions.cancel((Long) authentication.getPrincipal(), actionId);
    }
}
