package com.teamproject.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.services.blocking.ResponseService;
import com.teamproject.aiprovider.infrastructure.openai.DynamicOpenAiSettings;
import com.teamproject.aiusage.application.AiUsageRecorder;
import com.teamproject.aiusage.domain.AiUsageOperation;
import com.teamproject.assistant.application.port.AiAssistantGateway.Decision;
import com.teamproject.assistant.application.port.AiAssistantGateway.TextDecision;
import com.teamproject.assistant.infrastructure.openai.OpenAiAssistantGateway;
import com.teamproject.assistant.infrastructure.openai.OpenAiAssistantProperties;
import com.teamproject.common.exception.ApplicationException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenAiAssistantGatewayTest {
    private final ObjectMapper json = new ObjectMapper();
    private final OpenAIClient client = mock(OpenAIClient.class);
    private final ResponseService responses = mock(ResponseService.class);
    private final AiUsageRecorder recorder = mock(AiUsageRecorder.class);

    @BeforeEach
    void setUp() {
        when(client.responses()).thenReturn(responses);
    }

    @Test
    void recordsUsageAfterReturningAValidAssistantDecision() throws Exception {
        when(responses.create(any(ResponseCreateParams.class))).thenReturn(completedResponse());

        Decision decision = gateway(true, "gpt-5.6-luna", "sk-test-key")
                .decide("context", List.of(), "question", null);

        assertThat(decision).isEqualTo(new TextDecision("확인했습니다."));
        verify(recorder).success(AiUsageOperation.ASSISTANT_RESPONSE,
                "gpt-5.6-luna", 11L, 7L, 18L);
    }

    @Test
    void recordsTheExistingAssistantFailureCodeWhenTheProviderCallFails() {
        when(responses.create(any(ResponseCreateParams.class))).thenThrow(new RuntimeException("provider unavailable"));

        assertThatThrownBy(() -> gateway(true, "gpt-5.6-luna", "sk-test-key")
                .decide("context", List.of(), "question", null))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.code()).isEqualTo("AI_ASSISTANT_UNAVAILABLE"));

        verify(recorder).failure(AiUsageOperation.ASSISTANT_RESPONSE,
                "gpt-5.6-luna", "AI_ASSISTANT_UNAVAILABLE");
    }

    @Test
    void recordsAnIncompleteAssistantResponseAsAFailedAttempt() throws Exception {
        when(responses.create(any(ResponseCreateParams.class))).thenReturn(responseWithStatus("incomplete"));

        assertThatThrownBy(() -> gateway(true, "gpt-5.6-luna", "sk-test-key")
                .decide("context", List.of(), "question", null))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.code()).isEqualTo("AI_ASSISTANT_UNAVAILABLE"));

        verify(recorder).failure(AiUsageOperation.ASSISTANT_RESPONSE,
                "gpt-5.6-luna", "AI_ASSISTANT_UNAVAILABLE");
    }

    @Test
    void doesNotRecordWhenAssistantConfigurationPreventsAProviderCall() {
        assertThatThrownBy(() -> gateway(false, "gpt-5.6-luna", "sk-test-key")
                .decide("context", List.of(), "question", null))
                .isInstanceOf(ApplicationException.class);

        verifyNoInteractions(recorder);
    }

    private OpenAiAssistantGateway gateway(boolean enabled, String model, String apiKey) {
        OpenAiAssistantProperties assistant = new OpenAiAssistantProperties(
                enabled, model, Duration.ofSeconds(30), 800L, "text-embedding-3-small");
        DynamicOpenAiSettings openAi = mock(DynamicOpenAiSettings.class);
        when(openAi.assistantEnabled()).thenReturn(enabled);
        when(openAi.hasApiKey()).thenReturn(apiKey != null && !apiKey.isBlank());
        when(openAi.ready()).thenReturn(apiKey != null && !apiKey.isBlank());
        when(openAi.chatModel()).thenReturn(model);
        when(openAi.assistantClient()).thenReturn(client);
        return new OpenAiAssistantGateway(openAi, assistant, recorder);
    }

    private Response completedResponse() throws Exception {
        return responseWithStatus("completed");
    }

    private Response responseWithStatus(String status) throws Exception {
        String response = json.writeValueAsString(Map.of(
                "id", "resp_test_001",
                "object", "response",
                "created_at", 1785222000,
                "status", status,
                "model", "gpt-5.6-luna",
                "usage", Map.of("input_tokens", 11, "output_tokens", 7, "total_tokens", 18),
                "output", List.of(Map.of(
                        "id", "msg_test",
                        "type", "message",
                        "role", "assistant",
                        "status", "completed",
                        "content", List.of(Map.of(
                                "type", "output_text",
                                "annotations", List.of(),
                                "text", "확인했습니다."))))));
        return ObjectMappers.jsonMapper().readValue(response, Response.class);
    }
}
