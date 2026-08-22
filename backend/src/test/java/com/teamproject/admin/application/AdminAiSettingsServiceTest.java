package com.teamproject.admin.application;

import com.openai.client.OpenAIClient;
import com.openai.services.blocking.ModelService;
import com.teamproject.assistant.infrastructure.openai.OpenAiAssistantProperties;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAiSettingsServiceTest {

    private OpenAiReportProperties reportProperties(boolean enabled, String apiKey, String model) {
        return new OpenAiReportProperties(enabled, apiKey, model, null, Duration.ofSeconds(45), 1, 3000L, null);
    }

    private OpenAiAssistantProperties assistantProperties(boolean enabled, String model) {
        return new OpenAiAssistantProperties(enabled, model, Duration.ofSeconds(30), 800L);
    }

    private OpenAIClient clientReturning(ModelService models) {
        OpenAIClient client = mock(OpenAIClient.class);
        when(client.models()).thenReturn(models);
        return client;
    }

    @Test
    void statusReportsConfigurationWithoutExposingTheFullKey() {
        AdminAiSettingsService service = new AdminAiSettingsService(
                reportProperties(true, "sk-secret-value-1234", "gpt-5.6-luna"),
                assistantProperties(true, "gpt-5.6-sol"),
                clientReturning(mock(ModelService.class)),
                clientReturning(mock(ModelService.class)),
                "gpt-5.6-sol,gpt-5.6-luna");

        var status = service.status();

        assertThat(status.report().apiKeyConfigured()).isTrue();
        assertThat(status.report().maskedApiKey()).isEqualTo("****1234");
        assertThat(status.report().maskedApiKey()).doesNotContain("secret");
        assertThat(status.supportedModels()).containsExactly("gpt-5.6-sol", "gpt-5.6-luna");
    }

    @Test
    void connectionTestSucceedsWhenModelIsRetrievable() {
        ModelService models = mock(ModelService.class);
        when(models.retrieve(anyString())).thenReturn(null);
        OpenAIClient client = clientReturning(models);

        AdminAiSettingsService service = new AdminAiSettingsService(
                reportProperties(true, "sk-secret-value-1234", "gpt-5.6-luna"),
                assistantProperties(true, "gpt-5.6-sol"),
                client, client, "gpt-5.6-sol,gpt-5.6-luna");

        var result = service.testConnections();

        assertThat(result.report().success()).isTrue();
        assertThat(result.assistant().success()).isTrue();
    }

    @Test
    void connectionTestFailsWhenApiKeyIsMissing() {
        AdminAiSettingsService service = new AdminAiSettingsService(
                reportProperties(true, "", "gpt-5.6-luna"),
                assistantProperties(true, "gpt-5.6-sol"),
                clientReturning(mock(ModelService.class)),
                clientReturning(mock(ModelService.class)),
                "gpt-5.6-sol,gpt-5.6-luna");

        var result = service.testConnections();

        assertThat(result.report().success()).isFalse();
        assertThat(result.report().message()).contains("API 키");
    }

    @Test
    void connectionTestFailsWhenTheModelCallThrows() {
        ModelService models = mock(ModelService.class);
        when(models.retrieve(anyString())).thenThrow(new RuntimeException("401 Unauthorized"));
        OpenAIClient client = clientReturning(models);

        AdminAiSettingsService service = new AdminAiSettingsService(
                reportProperties(true, "sk-secret-value-1234", "gpt-5.6-luna"),
                assistantProperties(true, "gpt-5.6-sol"),
                client, client, "gpt-5.6-sol,gpt-5.6-luna");

        var result = service.testConnections();

        assertThat(result.report().success()).isFalse();
        assertThat(result.report().message()).contains("연결에 실패");
    }
}
