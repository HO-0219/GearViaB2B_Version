package com.teamproject.admin.application;

import com.openai.client.OpenAIClient;
import com.teamproject.assistant.infrastructure.openai.OpenAiAssistantProperties;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Read-only view of the AI provider configuration plus a connection test.
 * The OpenAI API key is intentionally never accepted or changed through this
 * service — it stays managed by installer/commands/configure-ai.sh (root-only
 * file, never rendered in the web app) so the key never has to pass through
 * the browser or the application database.
 */
@Service
public class AdminAiSettingsService {
    private final OpenAiReportProperties reportProperties;
    private final OpenAiAssistantProperties assistantProperties;
    private final OpenAIClient reportClient;
    private final OpenAIClient assistantClient;
    private final List<String> supportedModels;

    public AdminAiSettingsService(OpenAiReportProperties reportProperties, OpenAiAssistantProperties assistantProperties,
            @Qualifier("openAiReportClient") OpenAIClient reportClient,
            @Qualifier("openAiAssistantClient") OpenAIClient assistantClient,
            @Value("${app.ai.supported-models:gpt-5.6-sol,gpt-5.6-luna}") String supportedModelsCsv) {
        this.reportProperties = reportProperties;
        this.assistantProperties = assistantProperties;
        this.reportClient = reportClient;
        this.assistantClient = assistantClient;
        this.supportedModels = Arrays.stream(supportedModelsCsv.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    public StatusResponse status() {
        return new StatusResponse(
                verticalStatus(reportProperties.enabled(), reportProperties.model(), reportProperties.baseUrl()),
                verticalStatus(assistantProperties.enabled(), assistantProperties.model(), reportProperties.baseUrl()),
                supportedModels);
    }

    public ConnectionTestResponse testConnections() {
        return new ConnectionTestResponse(
                testVertical(reportClient, reportProperties.enabled(), reportProperties.model()),
                testVertical(assistantClient, assistantProperties.enabled(), assistantProperties.model()));
    }

    private VerticalStatus verticalStatus(boolean enabled, String model, String baseUrl) {
        return new VerticalStatus(enabled, reportProperties.hasApiKey(), maskedKey(), model, baseUrl);
    }

    private VerticalTestResult testVertical(OpenAIClient client, boolean enabled, String model) {
        if (!enabled) return new VerticalTestResult(false, "AI 기능이 비활성화되어 있습니다.");
        if (!reportProperties.hasApiKey()) return new VerticalTestResult(false, "API 키가 설정되지 않았습니다.");
        if (model == null || model.isBlank()) return new VerticalTestResult(false, "모델이 설정되지 않았습니다.");
        try {
            client.models().retrieve(model);
            return new VerticalTestResult(true, "연결에 성공했습니다.");
        } catch (Exception exception) {
            return new VerticalTestResult(false, "연결에 실패했습니다: " + exception.getClass().getSimpleName());
        }
    }

    private String maskedKey() {
        String key = reportProperties.apiKey();
        if (key == null || key.isBlank()) return null;
        String trimmed = key.trim();
        return "****" + trimmed.substring(Math.max(0, trimmed.length() - 4));
    }

    public record VerticalStatus(boolean enabled, boolean apiKeyConfigured, String maskedApiKey, String model, String baseUrl) {}
    public record StatusResponse(VerticalStatus report, VerticalStatus assistant, List<String> supportedModels) {}
    public record VerticalTestResult(boolean success, String message) {}
    public record ConnectionTestResponse(VerticalTestResult report, VerticalTestResult assistant) {}
}
