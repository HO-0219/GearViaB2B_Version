package com.teamproject.resource.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class NasMigrationServiceTest {
    @TempDir Path localRoot;
    @TempDir Path nasRoot;

    @Test
    void verifiesBytesAndContentTypeBeforeSwitching() {
        LocalFileStorage source = new LocalFileStorage(localRoot.toString());
        NasFileStorage target = new NasFileStorage(nasRoot.toString());
        source.put("docs/a.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        AtomicBoolean switched = new AtomicBoolean();

        NasMigrationService service = new NasMigrationService();
        NasMigrationService.NasPreflight preflight = service.preflight(localRoot, nasRoot, source);
        NasMigrationService.MigrationResult result = service.migrateAndVerify(source, target,
                () -> switched.set(true));

        assertThat(preflight.success()).isTrue();
        assertThat(preflight.sourceFiles()).isEqualTo(1);
        assertThat(preflight.sourceBytes()).isEqualTo(5);
        assertThat(preflight.targetFreeBytes()).isGreaterThan(preflight.requiredBytes());
        assertThat(preflight.mountIdentity()).isNotBlank();
        assertThat(result.success()).isTrue();
        assertThat(result.verifiedFiles()).isEqualTo(1);
        assertThat(result.verifiedBytes()).isEqualTo(5);
        assertThat(switched).isTrue();
        assertThat(target.get("docs/a.txt").contentType()).isEqualTo("text/plain");
    }

    @Test
    void copyFailureDoesNotRunTheProviderSwitch() {
        LocalFileStorage source = new LocalFileStorage(localRoot.toString());
        source.put("docs/a.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        FileStorage failingTarget = new FileStorage() {
            @Override public void put(String key, byte[] content, String contentType) {
                throw new IllegalStateException("simulated target failure");
            }
            @Override public StoredFile get(String key) { throw new UnsupportedOperationException(); }
            @Override public void delete(String key) {}
            @Override public java.util.List<String> listKeys() { return java.util.List.of(); }
        };
        AtomicBoolean switched = new AtomicBoolean();

        NasMigrationService.MigrationResult result = new NasMigrationService()
                .migrateAndVerify(source, failingTarget, () -> switched.set(true));

        assertThat(result.success()).isFalse();
        assertThat(result.failureCode()).isEqualTo("NAS_COPY_FAILED");
        assertThat(switched).isFalse();
    }

    @Test
    void verificationMismatchDoesNotRunTheProviderSwitch() {
        LocalFileStorage source = new LocalFileStorage(localRoot.toString());
        source.put("docs/a.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        FileStorage corruptingTarget = new FileStorage() {
            @Override public void put(String key, byte[] content, String contentType) {}
            @Override public StoredFile get(String key) {
                return new StoredFile("wrong".getBytes(StandardCharsets.UTF_8), "text/plain");
            }
            @Override public void delete(String key) {}
            @Override public java.util.List<String> listKeys() { return java.util.List.of("docs/a.txt"); }
        };
        AtomicBoolean switched = new AtomicBoolean();

        NasMigrationService.MigrationResult result = new NasMigrationService()
                .migrateAndVerify(source, corruptingTarget, () -> switched.set(true));

        assertThat(result.success()).isFalse();
        assertThat(result.failureCode()).isEqualTo("NAS_VERIFY_FAILED");
        assertThat(switched).isFalse();
    }

    @Test
    void preflightRejectsFilesAndDatasetsLargerThanTheConfiguredLimits() {
        LocalFileStorage source = new LocalFileStorage(localRoot.toString());
        source.put("big.bin", new byte[64], "application/octet-stream");

        assertThat(new NasMigrationService(32, 4096).preflight(localRoot, nasRoot, source).failureCode())
                .isEqualTo("NAS_FILE_TOO_LARGE");
        assertThat(new NasMigrationService(4096, 32).preflight(localRoot, nasRoot, source).failureCode())
                .isEqualTo("NAS_DATASET_TOO_LARGE");
    }

    @Test
    void aPartialCopyFailureRemovesTheFilesAlreadyWrittenToTheTarget() {
        LocalFileStorage source = new LocalFileStorage(localRoot.toString());
        source.put("a.txt", "one".getBytes(StandardCharsets.UTF_8), "text/plain");
        source.put("b.txt", "two".getBytes(StandardCharsets.UTF_8), "text/plain");
        NasFileStorage realTarget = new NasFileStorage(nasRoot.toString());
        FileStorage target = new FileStorage() {
            @Override public void put(String key, byte[] content, String contentType) {
                if (key.equals("b.txt")) throw new IllegalStateException("simulated mid-migration failure");
                realTarget.put(key, content, contentType);
            }
            @Override public StoredFile get(String key) { return realTarget.get(key); }
            @Override public void delete(String key) { realTarget.delete(key); }
            @Override public java.util.List<String> listKeys() { return realTarget.listKeys(); }
        };

        NasMigrationService.MigrationResult result = new NasMigrationService()
                .migrateAndVerify(source, target, () -> { throw new AssertionError("must not switch"); });

        assertThat(result.failureCode()).isEqualTo("NAS_COPY_FAILED");
        assertThat(realTarget.listKeys()).isEmpty();
    }

    @Test
    void rollbackCopiesVerifiedDataBeforeRestoringThePreviousProvider() {
        LocalFileStorage previous = new LocalFileStorage(localRoot.toString());
        NasFileStorage current = new NasFileStorage(nasRoot.toString());
        current.put("docs/new.txt", "new".getBytes(StandardCharsets.UTF_8), "text/plain");
        AtomicBoolean restored = new AtomicBoolean();

        NasMigrationService.MigrationResult result = new NasMigrationService()
                .rollback(current, previous, () -> restored.set(true));

        assertThat(result.success()).isTrue();
        assertThat(restored).isTrue();
        assertThat(new String(previous.get("docs/new.txt").content(), StandardCharsets.UTF_8))
                .isEqualTo("new");
    }
}
