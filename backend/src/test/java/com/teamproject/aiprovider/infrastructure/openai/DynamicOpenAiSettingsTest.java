package com.teamproject.aiprovider.infrastructure.openai;

import com.teamproject.admin.security.AdminMfaCipher;
import com.teamproject.aiprovider.domain.AiProviderSettings;
import com.teamproject.aiprovider.domain.AiProviderSettingsRepository;
import com.teamproject.assistant.infrastructure.openai.OpenAiAssistantProperties;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicOpenAiSettingsTest {
    private final AiProviderSettingsRepository settings = mock(AiProviderSettingsRepository.class);
    private final AdminMfaCipher cipher = new AdminMfaCipher(
            Base64.getEncoder().encodeToString(new byte[32]));
    private final OpenAiReportProperties reportDefaults = new OpenAiReportProperties(
            true, "sk-env-default", "gpt-5.6-luna", null, Duration.ofSeconds(45), 1, 3000L, null);
    private final OpenAiAssistantProperties assistantDefaults = new OpenAiAssistantProperties(
            false, "gpt-5.6-sol", Duration.ofSeconds(30), 800L, null);

    @Test
    void fallsBackToEnvDefaultsWhenNoRowIsPersisted() {
        when(settings.findById(AiProviderSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        DynamicOpenAiSettings value = new DynamicOpenAiSettings(settings, cipher, reportDefaults, assistantDefaults);

        assertThat(value.hasApiKey()).isTrue();
        assertThat(value.maskedApiKey()).isEqualTo("****ault");
        assertThat(value.reportEnabled()).isTrue();
        assertThat(value.assistantEnabled()).isFalse();
    }

    @Test
    void aPersistedRowIsAuthoritativeEvenWithoutAKey() {
        AiProviderSettings persisted = new AiProviderSettings(null, false, true);
        when(settings.findById(AiProviderSettings.SINGLETON_ID)).thenReturn(Optional.of(persisted));

        DynamicOpenAiSettings value = new DynamicOpenAiSettings(settings, cipher, reportDefaults, assistantDefaults);

        assertThat(value.hasApiKey()).isFalse();
        assertThat(value.reportEnabled()).isFalse();
        assertThat(value.assistantEnabled()).isTrue();
    }

    @Test
    void decryptsAPersistedKey() {
        AiProviderSettings persisted = new AiProviderSettings(cipher.encrypt("sk-stored-secret"), true, true);
        when(settings.findById(AiProviderSettings.SINGLETON_ID)).thenReturn(Optional.of(persisted));

        DynamicOpenAiSettings value = new DynamicOpenAiSettings(settings, cipher, reportDefaults, assistantDefaults);

        assertThat(value.hasApiKey()).isTrue();
        assertThat(value.maskedApiKey()).isEqualTo("****cret");
    }

    @Test
    void updateEncryptsAndPersistsANewKeyAndRebuildsClients() {
        when(settings.findById(AiProviderSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(settings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DynamicOpenAiSettings value = new DynamicOpenAiSettings(settings, cipher, reportDefaults, assistantDefaults);
        var clientBefore = value.reportClient();

        value.update("sk-new-key-9999", true, false);

        assertThat(value.hasApiKey()).isTrue();
        assertThat(value.maskedApiKey()).isEqualTo("****9999");
        assertThat(value.reportEnabled()).isTrue();
        assertThat(value.assistantEnabled()).isFalse();
        assertThat(value.reportClient()).isNotSameAs(clientBefore);
        verify(settings).save(any());
    }

    @Test
    void updateWithABlankKeyClearsIt() {
        AiProviderSettings persisted = new AiProviderSettings(cipher.encrypt("sk-stored-secret"), true, true);
        when(settings.findById(AiProviderSettings.SINGLETON_ID)).thenReturn(Optional.of(persisted));
        DynamicOpenAiSettings value = new DynamicOpenAiSettings(settings, cipher, reportDefaults, assistantDefaults);

        value.update("", false, false);

        assertThat(value.hasApiKey()).isFalse();
        assertThat(value.maskedApiKey()).isNull();
    }

    @Test
    void updateWithoutTouchingTheKeyKeepsTheExistingOne() {
        AiProviderSettings persisted = new AiProviderSettings(cipher.encrypt("sk-stored-secret"), false, false);
        when(settings.findById(AiProviderSettings.SINGLETON_ID)).thenReturn(Optional.of(persisted));
        DynamicOpenAiSettings value = new DynamicOpenAiSettings(settings, cipher, reportDefaults, assistantDefaults);

        value.update(null, true, true);

        assertThat(value.hasApiKey()).isTrue();
        assertThat(value.maskedApiKey()).isEqualTo("****cret");
        assertThat(value.reportEnabled()).isTrue();
    }

    @Test
    void updateRejectsANewKeyWhenTheEncryptionKeyIsNotConfigured() {
        AdminMfaCipher unconfiguredCipher = new AdminMfaCipher("");
        when(settings.findById(AiProviderSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        DynamicOpenAiSettings value = new DynamicOpenAiSettings(settings, unconfiguredCipher, reportDefaults, assistantDefaults);

        assertThatThrownBy(() -> value.update("sk-new-key", true, true))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    void internalProviderDoesNotRequireAnApiKeyAndPersistsRuntimeRouting() {
        when(settings.findById(AiProviderSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(settings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DynamicOpenAiSettings value = new DynamicOpenAiSettings(settings, cipher, reportDefaults, assistantDefaults);

        value.update("", true, true, "INTERNAL_OPENAI_COMPATIBLE", "http://10.0.0.8:8000/v1",
                "company-model", "company-embed", 20, false);

        assertThat(value.ready()).isTrue();
        assertThat(value.provider()).isEqualTo("INTERNAL_OPENAI_COMPATIBLE");
        assertThat(value.baseUrl()).isEqualTo("http://10.0.0.8:8000/v1");
        assertThat(value.chatModel()).isEqualTo("company-model");
        assertThat(value.embeddingModel()).isEqualTo("company-embed");
        assertThat(value.requestTimeoutSeconds()).isEqualTo(20);
        assertThat(value.externalAllowed()).isFalse();
    }
}
