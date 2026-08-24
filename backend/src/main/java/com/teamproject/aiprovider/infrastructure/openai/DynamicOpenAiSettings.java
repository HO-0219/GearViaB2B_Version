package com.teamproject.aiprovider.infrastructure.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.teamproject.admin.security.AdminMfaCipher;
import com.teamproject.aiprovider.domain.AiProviderSettings;
import com.teamproject.aiprovider.domain.AiProviderSettingsRepository;
import com.teamproject.assistant.infrastructure.openai.OpenAiAssistantProperties;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

    private volatile boolean loaded;
    private volatile String apiKey;
    private volatile boolean reportEnabled;
    private volatile boolean assistantEnabled;
    private volatile OpenAIClient reportClient;
    private volatile OpenAIClient assistantClient;

    public DynamicOpenAiSettings(AiProviderSettingsRepository settings, AdminMfaCipher cipher,
            OpenAiReportProperties reportDefaults, OpenAiAssistantProperties assistantDefaults) {
        this.settings = settings;
        this.cipher = cipher;
        this.reportDefaults = reportDefaults;
        this.assistantDefaults = assistantDefaults;
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
        }, () -> {
            this.apiKey = reportDefaults.apiKey();
            this.reportEnabled = reportDefaults.enabled();
            this.assistantEnabled = assistantDefaults.enabled();
        });
        rebuildClients();
        loaded = true;
    }

    public OpenAIClient reportClient() { ensureLoaded(); return reportClient; }
    public OpenAIClient assistantClient() { ensureLoaded(); return assistantClient; }
    public boolean hasApiKey() { ensureLoaded(); return apiKey != null && !apiKey.isBlank(); }
    public boolean reportEnabled() { ensureLoaded(); return reportEnabled; }
    public boolean assistantEnabled() { ensureLoaded(); return assistantEnabled; }

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
        String resolvedKey = apiKeyOrNull == null ? apiKey : apiKeyOrNull.trim();
        if (resolvedKey != null && !resolvedKey.isEmpty() && !cipher.configured()) {
            throw new ApplicationException("AI_KEY_ENCRYPTION_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                    "관리자 암호화 키(ADMIN_MFA_ENCRYPTION_KEY_BASE64)가 설정되지 않아 API 키를 저장할 수 없습니다.");
        }
        String encrypted = (resolvedKey == null || resolvedKey.isEmpty()) ? null : cipher.encrypt(resolvedKey);
        AiProviderSettings value = settings.findById(AiProviderSettings.SINGLETON_ID)
                .map(existing -> { existing.update(encrypted, newReportEnabled, newAssistantEnabled); return existing; })
                .orElseGet(() -> new AiProviderSettings(encrypted, newReportEnabled, newAssistantEnabled));
        settings.save(value);

        this.apiKey = resolvedKey == null ? "" : resolvedKey;
        this.reportEnabled = newReportEnabled;
        this.assistantEnabled = newAssistantEnabled;
        rebuildClients();
    }

    private void rebuildClients() {
        this.reportClient = buildClient(reportDefaults.baseUrl(), reportDefaults.requestTimeout(), reportDefaults.maxRetries());
        this.assistantClient = buildClient(reportDefaults.baseUrl(), assistantDefaults.requestTimeout(), 1);
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
