package com.teamproject.resource.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * The single {@link FileStorage} bean the rest of the app injects. Delegates to a
 * local-disk or NAS-mount delegate depending on which provider is currently active,
 * and lets an admin switch that live from the web (see {@link #activateNas()}) instead
 * of requiring a container restart with a different STORAGE_PROVIDER env var — the
 * env var now only supplies the default for the very first boot.
 */
@Component
public class DynamicFileStorage implements FileStorage {
    private static final Logger log = LoggerFactory.getLogger(DynamicFileStorage.class);
    public static final String LOCAL = "local";
    public static final String NAS_MOUNT = "nas_mount";
    public static final List<String> SUPPORTED_PROVIDERS = List.of(LOCAL, NAS_MOUNT);
    private static final String PROBE_FILE_NAME = ".b2bgearvia-storage-probe";

    private final LocalFileStorage local;
    private final String localRoot;
    private final String nasRoot;
    private final StorageSettingsRepository settings;
    private final NasMigrationService migrations = new NasMigrationService();
    private volatile NasFileStorage nas;
    private volatile String active;

    public DynamicFileStorage(
            @Value("${app.storage.provider:local}") String defaultProvider,
            @Value("${app.storage.local-root:/opt/b2bgearvia/data/uploads}") String localRoot,
            @Value("${app.storage.nas-root:/opt/b2bgearvia/data/nas}") String nasRoot,
            StorageSettingsRepository settings) {
        this.localRoot = localRoot;
        this.local = new LocalFileStorage(localRoot);
        this.nasRoot = nasRoot;
        this.settings = settings;
        this.active = settings.findById(StorageSettings.SINGLETON_ID)
                .map(StorageSettings::getActiveProvider)
                .orElse(NAS_MOUNT.equalsIgnoreCase(defaultProvider) ? NAS_MOUNT : LOCAL);
        if (NAS_MOUNT.equals(active)) {
            try {
                this.nas = new NasFileStorage(nasRoot);
            } catch (RuntimeException exception) {
                log.warn("Storage provider is set to nas_mount but the mount isn't reachable at startup ({}) "
                        + "— serving from local disk until an admin re-tests the NAS connection.",
                        exception.getMessage());
            }
        }
    }

    @Override public void put(String key, byte[] content, String contentType) { delegate().put(key, content, contentType); }
    @Override public StoredFile get(String key) { return delegate().get(key); }
    @Override public void delete(String key) { delegate().delete(key); }
    @Override public List<String> listKeys() { return delegate().listKeys(); }

    private FileStorage delegate() {
        return NAS_MOUNT.equals(active) && nas != null ? nas : local;
    }

    public Status status() {
        boolean nasReachable = nas != null;
        return new Status(active, SUPPORTED_PROVIDERS, localRoot, Files.isDirectory(Path.of(localRoot)),
                nasRoot, nasReachable);
    }

    /** Non-mutating readiness check for only the provider currently selected by the administrator. */
    public ActiveHealth activeHealth() {
        String provider = active;
        Path root = Path.of(NAS_MOUNT.equals(provider) ? nasRoot : localRoot).toAbsolutePath().normalize();
        boolean available = Files.isDirectory(root) && Files.isReadable(root) && Files.isWritable(root);
        if (NAS_MOUNT.equals(provider)) {
            available = available && nas != null;
        }
        return new ActiveHealth(provider, available);
    }

    /** Checks reachability and writability without switching anything. */
    public TestResult testNas() {
        Path root = Path.of(nasRoot).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new TestResult(false, "NAS 마운트 경로에 접근할 수 없습니다: " + root);
        }
        Path probe = root.resolve(PROBE_FILE_NAME);
        try {
            Files.write(probe, "b2bgearvia".getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.readAllBytes(probe);
            Files.delete(probe);
            return new TestResult(true, "연결 확인됨: " + root);
        } catch (IOException exception) {
            return new TestResult(false, "NAS 경로에 쓰기 권한이 없습니다: " + root);
        }
    }

    /**
     * Tests the NAS mount and only switches to it if the test succeeds; otherwise the
     * active provider is unchanged. Files already stored under the currently-active
     * provider are copied over first so they stay reachable after the switch.
     */
    public TestResult activateNas() {
        NasMigrationService.MigrationResult result = migrateToNas();
        return new TestResult(result.success(), result.success()
                ? "NAS 복사 검증 및 전환이 완료되었습니다."
                : "NAS 전환에 실패했습니다: " + result.failureCode());
    }

    public void activateLocal() {
        rollbackToLocal();
    }

    public NasMigrationService.NasPreflight preflightNas() {
        return migrations.preflight(Path.of(localRoot), Path.of(nasRoot), delegate());
    }

    public NasMigrationService.MigrationResult migrateToNas() {
        if (NAS_MOUNT.equals(active) && nas != null) {
            return new NasMigrationService.MigrationResult(true, 0, 0, null);
        }
        TestResult probe = testNas();
        if (!probe.success()) return NasMigrationService.MigrationResult.failed("NAS_UNAVAILABLE");
        NasMigrationService.NasPreflight preflight = preflightNas();
        if (!preflight.success()) return NasMigrationService.MigrationResult.failed(preflight.failureCode());
        NasFileStorage target = new NasFileStorage(nasRoot);
        return migrations.migrateAndVerify(delegate(), target, () -> {
            persist(NAS_MOUNT);
            this.nas = target;
            this.active = NAS_MOUNT;
        });
    }

    public NasMigrationService.MigrationResult rollbackToLocal() {
        if (LOCAL.equals(active)) return new NasMigrationService.MigrationResult(true, 0, 0, null);
        return migrations.rollback(delegate(), local, () -> {
            persist(LOCAL);
            this.active = LOCAL;
        });
    }

    private void persist(String provider) {
        StorageSettings value = settings.findById(StorageSettings.SINGLETON_ID)
                .orElseGet(() -> new StorageSettings(provider));
        value.updateProvider(provider);
        settings.save(value);
    }

    public record Status(String provider, List<String> supportedProviders, String localRootPath,
            boolean localMounted, String nasRootPath, boolean nasMounted) {}

    public record ActiveHealth(String provider, boolean up) {}

    public record TestResult(boolean success, String message) {}
}
