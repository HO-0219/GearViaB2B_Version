package com.teamproject.admin.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Read-only view of the active file storage provider. Like the AI key, the
 * provider and its mount path are host-level configuration (STORAGE_PROVIDER,
 * STORAGE_NAS_ROOT) set before the app starts — B2bConfigurationValidator
 * already fails startup if a nas_mount deployment has no reachable root, so
 * this only surfaces what's active rather than letting it be changed live.
 */
@Service
public class AdminStorageSettingsService {
    private static final List<String> SUPPORTED_PROVIDERS = List.of("local", "nas_mount");

    private final String provider;
    private final String localRoot;
    private final String nasRoot;

    public AdminStorageSettingsService(
            @Value("${app.storage.provider:local}") String provider,
            @Value("${app.storage.local-root:/opt/b2bgearvia/data/uploads}") String localRoot,
            @Value("${app.storage.nas-root:}") String nasRoot) {
        this.provider = provider;
        this.localRoot = localRoot;
        this.nasRoot = nasRoot;
    }

    public StatusResponse status() {
        boolean isNas = "nas_mount".equalsIgnoreCase(provider);
        String activeRoot = isNas ? nasRoot : localRoot;
        return new StatusResponse(provider, SUPPORTED_PROVIDERS, activeRoot, mounted(activeRoot));
    }

    private boolean mounted(String root) {
        if (root == null || root.isBlank()) return false;
        return Files.isDirectory(Path.of(root));
    }

    public record StatusResponse(String provider, List<String> supportedProviders, String rootPath, boolean mounted) {}
}
