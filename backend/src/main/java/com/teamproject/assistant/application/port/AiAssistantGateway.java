package com.teamproject.assistant.application.port;

import java.util.List;

public interface AiAssistantGateway {
    Decision decide(String context, List<ChatMessage> history, String message);

    record ChatMessage(String role, String content) {}
    sealed interface Decision permits TextDecision, ToolDecision {}
    record TextDecision(String text) implements Decision {}
    record ToolDecision(String name, String argumentsJson) implements Decision {}
}
