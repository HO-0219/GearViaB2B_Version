package com.teamproject.assistant.application.port;

import java.util.List;

public interface AiAssistantGateway {
    /**
     * @param searchResult 자료 검색 결과 JSON. null 이면 1차 호출이라 search_documents 도구를 함께 준다.
     *                     값이 있으면 2차 호출이며 검색 도구는 빼서 검색만 반복하는 것을 막는다.
     */
    Decision decide(String context, List<ChatMessage> history, String message, String searchResult);

    record ChatMessage(String role, String content) {}
    sealed interface Decision permits TextDecision, ToolDecision {}
    record TextDecision(String text) implements Decision {}
    record ToolDecision(String name, String argumentsJson) implements Decision {}
}
