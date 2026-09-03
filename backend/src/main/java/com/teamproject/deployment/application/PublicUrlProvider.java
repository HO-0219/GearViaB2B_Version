package com.teamproject.deployment.application;

import com.teamproject.deployment.domain.DeploymentSettings;
import com.teamproject.deployment.domain.DeploymentSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Resolves the active public URL. The persisted single-row setting wins; when it
 * is absent the installer-provided bootstrap URL is used. No value is cached, so
 * an administrator change takes effect on the next request.
 */
@Component
public class PublicUrlProvider {

    private final DeploymentSettingsRepository settings;
    private final URI bootstrapUrl;

    public PublicUrlProvider(DeploymentSettingsRepository settings,
                             @Value("${app.frontend-url}") String bootstrapUrl) {
        this.settings = settings;
        this.bootstrapUrl = URI.create(bootstrapUrl);
    }

    public URI current() {
        return settings.findById(DeploymentSettings.SINGLETON_ID)
                .map(DeploymentSettings::publicUri)
                .orElse(bootstrapUrl);
    }

    public boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        URI candidate;
        try {
            candidate = new URI(origin.trim());
        } catch (URISyntaxException e) {
            return false;
        }
        return sameOrigin(candidate, current());
    }

    private static boolean sameOrigin(URI candidate, URI approved) {
        if (candidate.getScheme() == null || candidate.getHost() == null) {
            return false;
        }
        return candidate.getScheme().equalsIgnoreCase(approved.getScheme())
                && candidate.getHost().equalsIgnoreCase(approved.getHost())
                && effectivePort(candidate) == effectivePort(approved);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }
}
