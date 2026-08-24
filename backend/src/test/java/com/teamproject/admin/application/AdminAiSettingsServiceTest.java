package com.teamproject.admin.application;

import com.openai.client.OpenAIClient;
import com.openai.services.blocking.ModelService;
import com.teamproject.aiprovider.infrastructure.openai.DynamicOpenAiSettings;
import com.teamproject.assistant.infrastructure.openai.OpenAiAssistantProperties;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAiSettingsServiceTest {

    private OpenAiReportProperties reportProperties(String model) {
        return new OpenAiReportProperties(false, "", model, null, Duration.ofSeconds(45), 1, 3000L, null);
    }

    private OpenAiAssistantProperties assistantProperties(String model) {
        return new OpenAiAssistantProperties(false, model, Duration.ofSeconds(30), 800L, null);
    }

    private OpenAIClient clientReturning(ModelService models) {
        OpenAIClient client = mock(OpenAIClient.class);
        when(client.models()).thenReturn(models);
        return client;
    }

    private DynamicOpenAiSettings openAi(boolean reportEnabled, boolean assistantEnabled, boolean hasKey,
            String maskedKey, OpenAIClient reportClient, OpenAIClient assistantClient) {
        DynamicOpenAiSettings value = mock(DynamicOpenAiSettings.class);
        when(value.reportEnabled()).thenReturn(reportEnabled);
        when(value.assistantEnabled()).thenReturn(assistantEnabled);
        when(value.hasApiKey()).thenReturn(hasKey);
        when(value.maskedApiKey()).thenReturn(maskedKey);
        when(value.reportClient()).thenReturn(reportClient);
        when(value.assistantClient()).thenReturn(assistantClient);
        return value;
    }

    @Test
    void statusReportsConfigurationWithoutExposingTheFullKey() {
        AdminAiSettingsService service = new AdminAiSettingsService(
                reportProperties("gpt-5.6-luna"),
                assistantProperties("gpt-5.6-sol"),
                openAi(true, true, true, "****1234",
                        clientReturning(mock(ModelService.class)), clientReturning(mock(ModelService.class))),
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
                reportProperties("gpt-5.6-luna"), assistantProperties("gpt-5.6-sol"),
                openAi(true, true, true, "****1234", client, client),
                "gpt-5.6-sol,gpt-5.6-luna");

        var result = service.testConnections();

        assertThat(result.report().success()).isTrue();
        assertThat(result.assistant().success()).isTrue();
    }

    @Test
    void connectionTestFailsWhenApiKeyIsMissing() {
        AdminAiSettingsService service = new AdminAiSettingsService(
                reportProperties("gpt-5.6-luna"), assistantProperties("gpt-5.6-sol"),
                openAi(true, true, false, null,
                        clientReturning(mock(ModelService.class)), clientReturning(mock(ModelService.class))),
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
                reportProperties("gpt-5.6-luna"), assistantProperties("gpt-5.6-sol"),
                openAi(true, true, true, "****1234", client, client),
                "gpt-5.6-sol,gpt-5.6-luna");

        var result = service.testConnections();

        assertThat(result.report().success()).isFalse();
        assertThat(result.report().message()).contains("연결에 실패");
    }

    @Test
    void updateDelegatesToDynamicSettingsAndReturnsFreshStatus() {
        DynamicOpenAiSettings openAi = openAi(false, false, false, null,
                clientReturning(mock(ModelService.class)), clientReturning(mock(ModelService.class)));
        AdminAiSettingsService service = new AdminAiSettingsService(
                reportProperties("gpt-5.6-luna"), assistantProperties("gpt-5.6-sol"),
                openAi, "gpt-5.6-sol,gpt-5.6-luna");

        service.update("sk-new-key", true, true);

        verify(openAi).update("sk-new-key", true, true);
    }
}
