package com.teamproject.aiprovider.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Singleton row persisting the admin-set OpenAI API key (AES-GCM encrypted, see
 * {@code AdminMfaCipher}) and per-vertical enabled flags. Once this row exists it is
 * authoritative — falling back to env-configured defaults only happens when the row
 * is entirely absent (no admin has saved settings through the web yet).
 */
@Entity
@Table(name = "ai_provider_settings")
public class AiProviderSettings {
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;
    @Column(name = "api_key_encrypted", length = 1000)
    private String apiKeyEncrypted;
    @Column(name = "report_enabled", nullable = false)
    private boolean reportEnabled;
    @Column(name = "assistant_enabled", nullable = false)
    private boolean assistantEnabled;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AiProviderSettings() {}

    public AiProviderSettings(String apiKeyEncrypted, boolean reportEnabled, boolean assistantEnabled) {
        this.id = SINGLETON_ID;
        this.apiKeyEncrypted = apiKeyEncrypted;
        this.reportEnabled = reportEnabled;
        this.assistantEnabled = assistantEnabled;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String apiKeyEncrypted, boolean reportEnabled, boolean assistantEnabled) {
        this.apiKeyEncrypted = apiKeyEncrypted;
        this.reportEnabled = reportEnabled;
        this.assistantEnabled = assistantEnabled;
        this.updatedAt = LocalDateTime.now();
    }

    public String getApiKeyEncrypted() { return apiKeyEncrypted; }
    public boolean isReportEnabled() { return reportEnabled; }
    public boolean isAssistantEnabled() { return assistantEnabled; }
}
