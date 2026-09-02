package com.teamproject.aiprovider.infrastructure.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.teamproject.admin.security.AdminMfaCipher;
import com.teamproject.aiprovider.application.AiProviderPolicy;
import com.teamproject.aiprovider.application.AiProviderProfile;
import com.teamproject.aiprovider.domain.AiProviderSettings;
import com.teamproject.aiprovider.domain.AiProviderSettingsRepository;
import com.teamproject.assistant.infrastructure.openai.OpenAiAssistantProperties;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Owns the OpenAI API key and per-vertical enabled flags as admin-editable, runtime-
 * switchable state (see {@code DynamicFileStorage} for the same pattern applied to
 * storage). The two OpenAI SDK clients are immutable once built, so changing the key
 * rebuilds them instead of mutating in place. Model/base-URL/timeout stay fixed from
 * {@link OpenAiReportProperties}/{@link OpenAiAssistantProperties} — only the key and
 * the enabled flags are admin-configurable, deliberately not unifying the report and
 * assistant gateways themselves (see CLAUDE.md).
 */
@Component
public class DynamicOpenAiSettings {
    private static final Logger log = LoggerFactory.getLogger(DynamicOpenAiSettings.class);
    private static final String UNCONFIGURED_API_KEY = "not-configured";

    private final AiProviderSettingsRepository settings;
    private final AdminMfaCipher cipher;
    private final OpenAiReportProperties reportDefaults;
    private final OpenAiAssistantProperties assistantDefaults;
    private final AiProviderPolicy policy;

    private volatile boolean loaded;
    private volatile RuntimeState runtime;

    DynamicOpenAiSettings(AiProviderSettingsRepository settings, AdminMfaCipher cipher,
            OpenAiReportProperties reportDefaults, OpenAiAssistantProperties assistantDefaults) {
        this(settings, cipher, reportDefaults, assistantDefaults, new AiProviderPolicy());
    }

    @Autowired
    public DynamicOpenAiSettings(AiProviderSettingsRepository settings, AdminMfaCipher cipher,
            OpenAiReportProperties reportDefaults, OpenAiAssistantProperties assistantDefaults,
            AiProviderPolicy policy) {
        this.settings = settings;
        this.cipher = cipher;
        this.reportDefaults = reportDefaults;
        this.assistantDefaults = assistantDefaults;
        this.policy = policy;
    }

    /**
     * Loaded lazily on first access rather than in the constructor — this bean is
     * built during application-context startup, before every other singleton (and the
     * schema itself, for {@code ddl-auto=create-drop} test contexts) is guaranteed
     * ready. Nothing needs AI settings that early.
     */
    private synchronized void ensureLoaded() {
        if (loaded) return;
        settings.findById(AiProviderSettings.SINGLETON_ID).ifPresentOrElse(value -> {
            String key = value.getApiKeyEncrypted() == null ? "" : safeDecrypt(value.getApiKeyEncrypted());
            AiProviderProfile profile = persistedProfile(value);
            validateCredentialTransport(profile, key);
            this.runtime = buildRuntime(key, value.isReportEnabled(), value.isAssistantEnabled(), profile);
        }, () -> {
            AiProviderProfile profile = policy.validate("OPENAI", reportDefaults.baseUrl(), assistantDefaults.model(), assistantDefaults.embeddingModel(),
                    (int) assistantDefaults.requestTimeout().toSeconds(), true);
            this.runtime = buildRuntime(reportDefaults.apiKey(), reportDefaults.enabled(), assistantDefaults.enabled(), profile);
        });
        loaded = true;
    }

    private RuntimeState state() { ensureLoaded(); return runtime; }
    public OpenAIClient reportClient() { return state().reportClient(); }
    public OpenAIClient assistantClient() { return state().assistantClient(); }
    public boolean hasApiKey() { return !state().apiKey().isBlank(); }
    public boolean reportEnabled() { return state().reportEnabled(); }
    public boolean assistantEnabled() { return state().assistantEnabled(); }
    public String provider() { return state().profile().provider().name(); }
    public String baseUrl() { return state().profile().baseUrl(); }
    public String chatModel() { return state().profile().chatModel(); }
    public String embeddingModel() { return state().profile().embeddingModel(); }
    public int requestTimeoutSeconds() { return state().profile().requestTimeoutSeconds(); }
    public boolean externalAllowed() { return state().profile().externalAllowed(); }
    public boolean ready() {
        RuntimeState value = state();
        return value.profile().provider() == AiProviderProfile.Provider.INTERNAL_OPENAI_COMPATIBLE || !value.apiKey().isBlank();
    }

    public String maskedApiKey() {
        String trimmed = state().apiKey().trim();
        if (trimmed.isBlank()) return null;
        return "****" + trimmed.substring(Math.max(0, trimmed.length() - 4));
    }

    /**
     * apiKeyOrNull: null keeps the existing key, blank clears it, non-blank replaces it.
     * Rejects a non-blank key when the admin MFA encryption key isn't configured — the
     * key must never land in the database unencrypted.
     */
    @Transactional
    public void update(String apiKeyOrNull, boolean newReportEnabled, boolean newAssistantEnabled) {
        ensureLoaded();
        update(apiKeyOrNull, newReportEnabled, newAssistantEnabled, provider(), baseUrl(), chatModel(), embeddingModel(),
                requestTimeoutSeconds(), externalAllowed());
    }

    @Transactional
    public void update(String apiKeyOrNull, boolean newReportEnabled, boolean newAssistantEnabled,
            String providerValue, String baseUrl, String chatModel, String embeddingModel, int timeoutSeconds, boolean externalAllowed) {
        ensureLoaded();
        AiProviderProfile resolvedProfile = policy.validate(providerValue, baseUrl, chatModel, embeddingModel,
                timeoutSeconds, externalAllowed);
        String resolvedKey = apiKeyOrNull == null ? state().apiKey() : apiKeyOrNull.trim();
        validateCredentialTransport(resolvedProfile, resolvedKey);
        if (resolvedKey != null && !resolvedKey.isEmpty() && !cipher.configured()) {
            throw new ApplicationException("AI_KEY_ENCRYPTION_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                    "관리자 암호화 키(ADMIN_MFA_ENCRYPTION_KEY_BASE64)가 설정되지 않아 API 키를 저장할 수 없습니다.");
        }
        String encrypted = (resolvedKey == null || resolvedKey.isEmpty()) ? null : cipher.encrypt(resolvedKey);
        AiProviderSettings value = settings.findById(AiProviderSettings.SINGLETON_ID)
                .map(existing -> { existing.update(encrypted, newReportEnabled, newAssistantEnabled); return existing; })
                .orElseGet(() -> new AiProviderSettings(encrypted, newReportEnabled, newAssistantEnabled));
        value.updateProvider(resolvedProfile.provider().name(), resolvedProfile.baseUrl(), resolvedProfile.chatModel(), resolvedProfile.embeddingModel(),
                resolvedProfile.requestTimeoutSeconds(), resolvedProfile.externalAllowed());
        settings.saveAndFlush(value);

        RuntimeState next = buildRuntime(resolvedKey == null ? "" : resolvedKey,
                newReportEnabled, newAssistantEnabled, resolvedProfile);
        publishAfterCommit(next);
    }

    private RuntimeState buildRuntime(String apiKey, boolean reportEnabled, boolean assistantEnabled,
            AiProviderProfile profile) {
        java.time.Duration timeout = java.time.Duration.ofSeconds(profile.requestTimeoutSeconds());
        return new RuntimeState(apiKey, reportEnabled, assistantEnabled, profile,
                buildClient(apiKey, profile.baseUrl(), timeout, reportDefaults.maxRetries()),
                buildClient(apiKey, profile.baseUrl(), timeout, 1));
    }

    private AiProviderProfile persistedProfile(AiProviderSettings value) {
        String provider = value.getProviderType() == null ? "OPENAI" : value.getProviderType();
        String baseUrl = value.getBaseUrl() == null ? reportDefaults.baseUrl() : value.getBaseUrl();
        String model = value.getChatModel() == null ? assistantDefaults.model() : value.getChatModel();
        String embeddingModel = value.getEmbeddingModel() == null ? assistantDefaults.embeddingModel() : value.getEmbeddingModel();
        int timeout = value.getRequestTimeoutSeconds() == null
                ? (int) assistantDefaults.requestTimeout().toSeconds() : value.getRequestTimeoutSeconds();
        boolean external = value.getExternalAllowed() == null || value.getExternalAllowed();
        return policy.validate(provider, baseUrl, model, embeddingModel, timeout, external);
    }

    private OpenAIClient buildClient(String apiKey, String baseUrl, java.time.Duration timeout, int maxRetries) {
        boolean hasKey = apiKey != null && !apiKey.isBlank();
        return OpenAIOkHttpClient.builder()
                .apiKey(hasKey ? apiKey : UNCONFIGURED_API_KEY)
                .baseUrl(baseUrl)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .responseValidation(true)
                .build();
    }

    private String safeDecrypt(String encrypted) {
        try {
            return cipher.decrypt(encrypted);
        } catch (RuntimeException exception) {
            log.warn("Stored OpenAI API key could not be decrypted ({}) — treating AI integration as unconfigured "
                    + "until an admin re-enters the key.", exception.getMessage());
            return "";
        }
    }

    private void publishAfterCommit(RuntimeState next) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { runtime = next; }
            });
        } else {
            runtime = next;
        }
    }

    private void validateCredentialTransport(AiProviderProfile value, String credential) {
        if (value.provider() == AiProviderProfile.Provider.INTERNAL_OPENAI_COMPATIBLE
                && credential != null && !credential.isBlank() && value.baseUrl().startsWith("http://")) {
            throw new ApplicationException("AI_PROVIDER_TLS_REQUIRED", HttpStatus.BAD_REQUEST,
                    "API 키를 사용하는 사내 LLM은 HTTPS 연결이 필요합니다.");
        }
    }

    private record RuntimeState(String apiKey, boolean reportEnabled, boolean assistantEnabled,
            AiProviderProfile profile, OpenAIClient reportClient, OpenAIClient assistantClient) {}
}
