package com.teamproject.assistant.infrastructure.openai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-assistant")
public record OpenAiAssistantProperties(
        boolean enabled, String model, Duration requestTimeout, Long maxOutputTokens,
        String embeddingModel) {
    public OpenAiAssistantProperties {
        model = model == null ? "" : model.trim();
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
        maxOutputTokens = maxOutputTokens == null || maxOutputTokens <= 0 ? 800L : maxOutputTokens;
        embeddingModel = embeddingModel == null || embeddingModel.isBlank()
                ? "text-embedding-3-small" : embeddingModel.trim();
    }
}
