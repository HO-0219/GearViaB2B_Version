package com.teamproject.resource.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicFileStorageTest {
    @TempDir Path localRoot;
    @TempDir Path nasRoot;
    private final StorageSettingsRepository settings = mock(StorageSettingsRepository.class);

    @Test
    void defaultsToLocalAndServesThroughItWhenNoSettingIsPersisted() {
        when(settings.findById(StorageSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        DynamicFileStorage storage = new DynamicFileStorage("local", localRoot.toString(), nasRoot.toString(), settings);

        storage.put("a.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(localRoot.resolve("a.txt")).exists();
        assertThat(storage.status().provider()).isEqualTo("local");
    }

    @Test
    void activeLocalHealthIgnoresAnUnavailableInactiveNas() {
        when(settings.findById(StorageSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        DynamicFileStorage storage = new DynamicFileStorage("local", localRoot.toString(),
                nasRoot.resolve("missing").toString(), settings);

        assertThat(storage.activeHealth()).isEqualTo(new DynamicFileStorage.ActiveHealth("local", true));
    }

    @Test
    void activeNasHealthFailsWhenThePersistedMountIsUnavailable() {
        when(settings.findById(StorageSettings.SINGLETON_ID))
                .thenReturn(Optional.of(new StorageSettings("nas_mount")));
        DynamicFileStorage storage = new DynamicFileStorage("local", localRoot.toString(),
                nasRoot.resolve("missing").toString(), settings);

        assertThat(storage.activeHealth()).isEqualTo(new DynamicFileStorage.ActiveHealth("nas_mount", false));
    }

    @Test
    void testNasReportsFailureWhenTheMountIsUnreachableWithoutSwitchingAnything() {
        when(settings.findById(StorageSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        Path unmounted = nasRoot.resolve("not-mounted");
        DynamicFileStorage storage = new DynamicFileStorage("local", localRoot.toString(), unmounted.toString(), settings);

        DynamicFileStorage.TestResult result = storage.testNas();

        assertThat(result.success()).isFalse();
        assertThat(storage.status().provider()).isEqualTo("local");
    }

    @Test
    void testNasSucceedsAndProbeFileIsCleanedUpWhenTheMountIsWritable() {
        when(settings.findById(StorageSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        DynamicFileStorage storage = new DynamicFileStorage("local", localRoot.toString(), nasRoot.toString(), settings);

        DynamicFileStorage.TestResult result = storage.testNas();

        assertThat(result.success()).isTrue();
        assertThat(nasRoot).isEmptyDirectory();
    }

    @Test
    void activateNasSwitchesTheDelegateAndPersistsOnSuccess() {
        when(settings.findById(StorageSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        DynamicFileStorage storage = new DynamicFileStorage("local", localRoot.toString(), nasRoot.toString(), settings);

        DynamicFileStorage.TestResult result = storage.activateNas();
        storage.put("b.txt", "hi".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(result.success()).isTrue();
        assertThat(storage.status().provider()).isEqualTo("nas_mount");
        assertThat(nasRoot.resolve("b.txt")).exists();
        assertThat(localRoot.resolve("b.txt")).doesNotExist();
        verify(settings).save(any());
    }

    @Test
    void activateNasMigratesFilesThatWereStoredWhileLocalWasActive() {
        when(settings.findById(StorageSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        DynamicFileStorage storage = new DynamicFileStorage("local", localRoot.toString(), nasRoot.toString(), settings);
        storage.put("profile/pic.png", "old-bytes".getBytes(StandardCharsets.UTF_8), "image/png");

        storage.activateNas();

        FileStorage.StoredFile migrated = storage.get("profile/pic.png");
        assertThat(new String(migrated.content(), StandardCharsets.UTF_8)).isEqualTo("old-bytes");
        assertThat(migrated.contentType()).isEqualTo("image/png");
        assertThat(nasRoot.resolve("profile/pic.png")).exists();
    }

    @Test
    void activateLocalMigratesFilesThatWereStoredWhileNasWasActive() {
        when(settings.findById(StorageSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        DynamicFileStorage storage = new DynamicFileStorage("local", localRoot.toString(), nasRoot.toString(), settings);
        storage.activateNas();
        storage.put("profile/pic.png", "nas-bytes".getBytes(StandardCharsets.UTF_8), "image/png");

        storage.activateLocal();

        FileStorage.StoredFile migrated = storage.get("profile/pic.png");
        assertThat(new String(migrated.content(), StandardCharsets.UTF_8)).isEqualTo("nas-bytes");
        assertThat(localRoot.resolve("profile/pic.png")).exists();
    }

    @Test
    void activateNasLeavesTheProviderUnchangedWhenTheTestFails() {
        when(settings.findById(StorageSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        Path unmounted = nasRoot.resolve("not-mounted");
        DynamicFileStorage storage = new DynamicFileStorage("local", localRoot.toString(), unmounted.toString(), settings);

        DynamicFileStorage.TestResult result = storage.activateNas();
        storage.put("c.txt", "hi".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(result.success()).isFalse();
        assertThat(storage.status().provider()).isEqualTo("local");
        assertThat(localRoot.resolve("c.txt")).exists();
    }

    @Test
    void fallsBackToLocalAtStartupWhenThePersistedProviderIsNasButTheMountIsGone() {
        StorageSettings persisted = new StorageSettings("nas_mount");
        when(settings.findById(StorageSettings.SINGLETON_ID)).thenReturn(Optional.of(persisted));
        Path unmounted = nasRoot.resolve("gone");

        DynamicFileStorage storage = new DynamicFileStorage("local", localRoot.toString(), unmounted.toString(), settings);
        storage.put("d.txt", "hi".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(localRoot.resolve("d.txt")).exists();
    }

    @Test
    void activateLocalRevertsAndPersists() {
        when(settings.findById(StorageSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        DynamicFileStorage storage = new DynamicFileStorage("local", localRoot.toString(), nasRoot.toString(), settings);
        storage.activateNas();

        storage.activateLocal();
        storage.put("e.txt", "hi".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(storage.status().provider()).isEqualTo("local");
        assertThat(localRoot.resolve("e.txt")).exists();
    }
}
