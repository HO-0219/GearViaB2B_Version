package com.teamproject.deployment.application.dto;

/**
 * Views for the domain/TLS administration API. Certificate and private-key
 * bodies are deliberately excluded from every shape here.
 */
public final class DeploymentSettingsDtos {

    private DeploymentSettingsDtos() {
    }

    public record SettingsView(
            String publicUrl,
            String status,
            String certificateIssuer,
            String certificateNotAfter,
            String certificateSans,
            long applyVersion) {
    }

    public record JobView(
            long jobId,
            String type,
            String status,
            String publicUrl,
            int progressPercent,
            String verificationSummary,
            String failureCode,
            String rollbackSummary,
            long version) {
    }
}
