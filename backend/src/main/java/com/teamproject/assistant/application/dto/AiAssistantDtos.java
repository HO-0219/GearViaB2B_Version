package com.teamproject.assistant.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class AiAssistantDtos {
    private AiAssistantDtos() {}

    public record ChatRequest(
            @NotNull @Positive Long groupId,
            @NotBlank @Size(max = 2000) String message) {}
    public record ChatResponse(
            String message, Long pendingActionId, String actionType,
            String actionSummary, LocalDateTime expiresAt) {}
    public record ActionResponse(
            Long actionId, String status, String message, String targetUrl,
            String inviteUrl, Long selectedGroupId) {}
    public record MessageResponse(
            Long id, String role, String content, Long actionId, String actionType,
            String actionSummary, LocalDateTime actionExpiresAt, String actionStatus,
            LocalDateTime createdAt) {}
}
