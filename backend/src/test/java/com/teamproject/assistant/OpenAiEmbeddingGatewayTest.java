package com.teamproject.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.services.blocking.EmbeddingService;
import com.teamproject.aiusage.application.AiUsageRecorder;
import com.teamproject.aiusage.domain.AiUsageOperation;
import com.teamproject.assistant.infrastructure.openai.OpenAiAssistantProperties;
import com.teamproject.assistant.infrastructure.openai.OpenAiEmbeddingGateway;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
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

class OpenAiEmbeddingGatewayTest {
    private final ObjectMapper json = new ObjectMapper();
    private final OpenAIClient client = mock(OpenAIClient.class);
    private final EmbeddingService embeddings = mock(EmbeddingService.class);
    private final AiUsageRecorder recorder = mock(AiUsageRecorder.class);

    @BeforeEach
    void setUp() {
        when(client.embeddings()).thenReturn(embeddings);
    }

    @Test
    void recordsEmbeddingUsageAfterReturningVectors() throws Exception {
        when(embeddings.create(any(EmbeddingCreateParams.class))).thenReturn(completedResponse());

        List<float[]> vectors = gateway(true, "text-embedding-3-small", "sk-test-key")
                .embed(List.of("document text"));

        assertThat(vectors).hasSize(1);
        assertThat(vectors.getFirst()).containsExactly(0.1f, 0.2f);
        verify(recorder).success(AiUsageOperation.DOCUMENT_EMBEDDING,
                "text-embedding-3-small", 4L, 0L, 4L);
    }

    @Test
    void recordsTheExistingEmbeddingFailureCodeWhenTheProviderCallFails() {
        when(embeddings.create(any(EmbeddingCreateParams.class))).thenThrow(new RuntimeException("provider unavailable"));

        assertThatThrownBy(() -> gateway(true, "text-embedding-3-small", "sk-test-key")
                .embed(List.of("document text")))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.code()).isEqualTo("AI_ASSISTANT_EMBEDDING_FAILED"));

        verify(recorder).failure(AiUsageOperation.DOCUMENT_EMBEDDING,
                "text-embedding-3-small", "AI_ASSISTANT_EMBEDDING_FAILED");
    }

    @Test
    void doesNotRecordWhenEmbeddingConfigurationPreventsAProviderCall() {
        assertThatThrownBy(() -> gateway(false, "text-embedding-3-small", "sk-test-key")
                .embed(List.of("document text")))
                .isInstanceOf(ApplicationException.class);

        verifyNoInteractions(recorder);
    }

    private OpenAiEmbeddingGateway gateway(boolean enabled, String model, String apiKey) {
        OpenAiAssistantProperties assistant = new OpenAiAssistantProperties(
                enabled, "gpt-5.6-luna", Duration.ofSeconds(30), 800L, model);
        OpenAiReportProperties shared = new OpenAiReportProperties(
                false, apiKey, "", OpenAiReportProperties.DEFAULT_BASE_URL,
                Duration.ofSeconds(45), 1, 3000L, "test");
        return new OpenAiEmbeddingGateway(client, assistant, shared, recorder);
    }

    private CreateEmbeddingResponse completedResponse() throws Exception {
        String response = json.writeValueAsString(Map.of(
                "object", "list",
                "model", "text-embedding-3-small",
                "usage", Map.of("prompt_tokens", 4, "total_tokens", 4),
                "data", List.of(Map.of(
                        "object", "embedding",
                        "index", 0,
                        "embedding", List.of(0.1, 0.2)))));
        return ObjectMappers.jsonMapper().readValue(response, CreateEmbeddingResponse.class);
    }
}
