package com.teamproject.assistant.infrastructure.openai;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.teamproject.assistant.application.port.EmbeddingGateway;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OpenAiEmbeddingGateway implements EmbeddingGateway {
    private static final int BATCH = 64;

    private final OpenAIClient client;
    private final OpenAiAssistantProperties properties;
    private final OpenAiReportProperties sharedProperties;

    public OpenAiEmbeddingGateway(@Qualifier("openAiAssistantClient") OpenAIClient client,
            OpenAiAssistantProperties properties, OpenAiReportProperties sharedProperties) {
        this.client = client;
        this.properties = properties;
        this.sharedProperties = sharedProperties;
    }

    @Override
    public String modelId() {
        return properties.embeddingModel();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        if (!properties.enabled() || !sharedProperties.hasApiKey()
                || properties.embeddingModel().isBlank()) {
            throw new ApplicationException("AI_ASSISTANT_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 비서가 아직 활성화되지 않았습니다.");
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + BATCH));
            vectors.addAll(call(batch));
        }
        return vectors;
    }

    private List<float[]> call(List<String> batch) {
        var params = EmbeddingCreateParams.builder()
                .model(properties.embeddingModel())
                .inputOfArrayOfStrings(batch)
                .build();
        try {
            var response = client.embeddings().create(params);
            // 응답 순서를 신뢰하지 않고 index 로 되돌린다.
            float[][] ordered = new float[batch.size()][];
            response.data().forEach(embedding -> {
                List<Float> values = embedding.embedding();
                float[] vector = new float[values.size()];
                for (int index = 0; index < values.size(); index++) vector[index] = values.get(index);
                ordered[(int) embedding.index()] = vector;
            });
            for (float[] vector : ordered) {
                if (vector == null) throw new IllegalStateException("embedding response incomplete");
            }
            return List.of(ordered);
        } catch (ApplicationException exception) {
            throw exception;
        } catch (Exception exception) {
            // 요청 본문과 응답 원문은 남기지 않는다. 실패 사실만 계약된 코드로 올린다.
            throw new ApplicationException("AI_ASSISTANT_EMBEDDING_FAILED", HttpStatus.SERVICE_UNAVAILABLE,
                    "자료 검색 준비에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
