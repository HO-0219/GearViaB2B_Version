package com.teamproject.admin.application;

import com.openai.client.OpenAIClient;
import com.teamproject.aiprovider.infrastructure.openai.DynamicOpenAiSettings;
import com.teamproject.assistant.infrastructure.openai.OpenAiAssistantProperties;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Lets an admin view AI integration status, test the connection, and set the API key
 * plus per-vertical enabled flags from the web — backed by {@link DynamicOpenAiSettings}
 * (AES-GCM encrypted at rest, see AdminMfaCipher). The model stays fixed from
 * app.ai-report.model / app.ai-assistant.model — not admin-editable.
 */
@Service
public class AdminAiSettingsService {
    private final OpenAiReportProperties reportProperties;
    private final OpenAiAssistantProperties assistantProperties;
    private final DynamicOpenAiSettings openAi;
    private final List<String> supportedModels;

    public AdminAiSettingsService(OpenAiReportProperties reportProperties, OpenAiAssistantProperties assistantProperties,
            DynamicOpenAiSettings openAi,
            @Value("${app.ai.supported-models:gpt-5.6-sol,gpt-5.6-luna}") String supportedModelsCsv) {
        this.reportProperties = reportProperties;
        this.assistantProperties = assistantProperties;
        this.openAi = openAi;
        this.supportedModels = Arrays.stream(supportedModelsCsv.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    public StatusResponse status() {
        return new StatusResponse(
                verticalStatus(openAi.reportEnabled()),
                verticalStatus(openAi.assistantEnabled()),
                supportedModels, openAi.provider(), openAi.baseUrl(), openAi.chatModel(), openAi.embeddingModel(),
                openAi.requestTimeoutSeconds(), openAi.externalAllowed());
    }

    public ConnectionTestResponse testConnections() {
        return new ConnectionTestResponse(
                testVertical(openAi.reportClient(), openAi.reportEnabled(), openAi.chatModel()),
                testVertical(openAi.assistantClient(), openAi.assistantEnabled(), openAi.chatModel()));
    }

    /** apiKeyOrNull: null keeps the existing key, blank clears it, non-blank replaces it. */
    public StatusResponse update(String apiKeyOrNull, boolean reportEnabled, boolean assistantEnabled) {
        openAi.update(apiKeyOrNull, reportEnabled, assistantEnabled);
        return status();
    }

    public StatusResponse update(String apiKeyOrNull, boolean reportEnabled, boolean assistantEnabled,
            String provider, String baseUrl, String chatModel, String embeddingModel, int requestTimeoutSeconds, boolean externalAllowed) {
        openAi.update(apiKeyOrNull, reportEnabled, assistantEnabled, provider, baseUrl, chatModel, embeddingModel,
                requestTimeoutSeconds, externalAllowed);
        return status();
    }

    private VerticalStatus verticalStatus(boolean enabled) {
        return new VerticalStatus(enabled, openAi.hasApiKey(), openAi.maskedApiKey(), openAi.chatModel(), openAi.baseUrl());
    }

    private VerticalTestResult testVertical(OpenAIClient client, boolean enabled, String model) {
        if (!enabled) return new VerticalTestResult(false, "AI 기능이 비활성화되어 있습니다.");
        if (!openAi.ready()) return new VerticalTestResult(false, "API 키가 설정되지 않았습니다.");
        if (model == null || model.isBlank()) return new VerticalTestResult(false, "모델이 설정되지 않았습니다.");
        try {
            client.models().retrieve(model);
            return new VerticalTestResult(true, "연결에 성공했습니다.");
        } catch (Exception exception) {
            return new VerticalTestResult(false, "연결에 실패했습니다: " + exception.getClass().getSimpleName());
        }
    }

    public record VerticalStatus(boolean enabled, boolean apiKeyConfigured, String maskedApiKey, String model, String baseUrl) {}
    public record StatusResponse(VerticalStatus report, VerticalStatus assistant, List<String> supportedModels,
            String provider, String baseUrl, String chatModel, String embeddingModel, int requestTimeoutSeconds, boolean externalAllowed) {}
    public record VerticalTestResult(boolean success, String message) {}
    public record ConnectionTestResponse(VerticalTestResult report, VerticalTestResult assistant) {}
}
