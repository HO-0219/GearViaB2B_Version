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
    private volatile String apiKey;
    private volatile boolean reportEnabled;
    private volatile boolean assistantEnabled;
    private volatile OpenAIClient reportClient;
    private volatile OpenAIClient assistantClient;
    private volatile AiProviderProfile profile;

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
            this.apiKey = value.getApiKeyEncrypted() == null ? "" : safeDecrypt(value.getApiKeyEncrypted());
            this.reportEnabled = value.isReportEnabled();
            this.assistantEnabled = value.isAssistantEnabled();
            this.profile = persistedProfile(value);
        }, () -> {
            this.apiKey = reportDefaults.apiKey();
            this.reportEnabled = reportDefaults.enabled();
            this.assistantEnabled = assistantDefaults.enabled();
            this.profile = policy.validate("OPENAI", reportDefaults.baseUrl(), assistantDefaults.model(), assistantDefaults.embeddingModel(),
                    (int) assistantDefaults.requestTimeout().toSeconds(), true);
        });
        rebuildClients();
        loaded = true;
    }

    public OpenAIClient reportClient() { ensureLoaded(); return reportClient; }
    public OpenAIClient assistantClient() { ensureLoaded(); return assistantClient; }
    public boolean hasApiKey() { ensureLoaded(); return apiKey != null && !apiKey.isBlank(); }
    public boolean reportEnabled() { ensureLoaded(); return reportEnabled; }
    public boolean assistantEnabled() { ensureLoaded(); return assistantEnabled; }
    public String provider() { ensureLoaded(); return profile.provider().name(); }
    public String baseUrl() { ensureLoaded(); return profile.baseUrl(); }
    public String chatModel() { ensureLoaded(); return profile.chatModel(); }
    public String embeddingModel() { ensureLoaded(); return profile.embeddingModel(); }
    public int requestTimeoutSeconds() { ensureLoaded(); return profile.requestTimeoutSeconds(); }
    public boolean externalAllowed() { ensureLoaded(); return profile.externalAllowed(); }
    public boolean ready() {
        ensureLoaded();
        return profile.provider() == AiProviderProfile.Provider.INTERNAL_OPENAI_COMPATIBLE || hasApiKey();
    }

    public String maskedApiKey() {
        ensureLoaded();
        if (!hasApiKey()) return null;
        String trimmed = apiKey.trim();
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
        String resolvedKey = apiKeyOrNull == null ? apiKey : apiKeyOrNull.trim();
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
        settings.save(value);

        this.apiKey = resolvedKey == null ? "" : resolvedKey;
        this.reportEnabled = newReportEnabled;
        this.assistantEnabled = newAssistantEnabled;
        this.profile = resolvedProfile;
        rebuildClients();
    }

    private void rebuildClients() {
        java.time.Duration timeout = java.time.Duration.ofSeconds(profile.requestTimeoutSeconds());
        this.reportClient = buildClient(profile.baseUrl(), timeout, reportDefaults.maxRetries());
        this.assistantClient = buildClient(profile.baseUrl(), timeout, 1);
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

    private OpenAIClient buildClient(String baseUrl, java.time.Duration timeout, int maxRetries) {
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
}
