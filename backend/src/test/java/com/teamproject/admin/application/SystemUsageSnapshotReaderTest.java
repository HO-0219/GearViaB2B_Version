package com.teamproject.admin.application;

import com.teamproject.admin.application.SystemUsageProbe.Space;
import com.teamproject.admin.application.SystemUsageSnapshotReader.Capacity;
import com.teamproject.admin.application.SystemUsageSnapshotReader.Metric;
import com.teamproject.resource.storage.DynamicFileStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemUsageSnapshotReaderTest {
    @Test
    void convertsAvailableProbeValuesToCurrentUsageCards() {
        SystemUsageProbe probe = new FakeProbe(OptionalDouble.of(0.25),
                OptionalLong.of(800L), OptionalLong.of(200L),
                Optional.of(new Space(1_000L, 400L)));

        SystemUsageSnapshotReader.Snapshot snapshot = new SystemUsageSnapshotReader(probe, mock(DynamicFileStorage.class)).read();

        assertThat(snapshot.cpu()).isEqualTo(new Metric(true, 25.0));
        assertThat(snapshot.memory()).isEqualTo(new Capacity(true, 600L, 800L, 75.0));
        assertThat(snapshot.storage()).isEqualTo(new Capacity(true, 600L, 1_000L, 60.0));
    }

    @Test
    void keepsMemoryAndStorageVisibleWhenCpuLoadIsUnavailable() {
        SystemUsageProbe probe = new FakeProbe(OptionalDouble.empty(),
                OptionalLong.of(800L), OptionalLong.of(200L),
                Optional.of(new Space(1_000L, 400L)));

        SystemUsageSnapshotReader.Snapshot snapshot = new SystemUsageSnapshotReader(probe, mock(DynamicFileStorage.class)).read();

        assertThat(snapshot.cpu().available()).isFalse();
        assertThat(snapshot.memory().usedBytes()).isEqualTo(600L);
        assertThat(snapshot.storage().usedBytes()).isEqualTo(600L);
    }

    @Test
    void marksOnlyInvalidOrMissingProbeValuesAsUnavailable() {
        SystemUsageProbe probe = new FakeProbe(OptionalDouble.of(-1.0),
                OptionalLong.of(100L), OptionalLong.of(200L), Optional.empty());

        SystemUsageSnapshotReader.Snapshot snapshot = new SystemUsageSnapshotReader(probe, mock(DynamicFileStorage.class)).read();

        assertThat(snapshot.cpu()).isEqualTo(new Metric(false, null));
        assertThat(snapshot.memory()).isEqualTo(new Capacity(false, null, null, null));
        assertThat(snapshot.storage()).isEqualTo(new Capacity(false, null, null, null));
    }

    @Test
    void treatsAnInaccessibleNasMountAsUnavailable(@TempDir Path temporaryDirectory) {
        DynamicFileStorage storage = mock(DynamicFileStorage.class);
        when(storage.status()).thenReturn(new DynamicFileStorage.Status("nas_mount", List.of("local", "nas_mount"),
                temporaryDirectory.toString(), true,
                temporaryDirectory.resolve("missing-nas-mount").toString(), false));
        JdkSystemUsageProbe probe = new JdkSystemUsageProbe(storage);

        assertThat(probe.storageSpace()).isEmpty();
    }

    @Test
    void treatsAnInvalidStorageRootAsUnavailable() {
        DynamicFileStorage storage = mock(DynamicFileStorage.class);
        when(storage.status()).thenReturn(new DynamicFileStorage.Status("local", List.of("local", "nas_mount"),
                " ", false, "", false));
        JdkSystemUsageProbe probe = new JdkSystemUsageProbe(storage);

        assertThat(probe.storageSpace()).isEmpty();
    }

    @Test
    void storageSpaceFollowsTheCurrentlyActiveProviderRatherThanTheRootPassedAtConstruction(
            @TempDir Path localDirectory, @TempDir Path nasDirectory) throws IOException {
        DynamicFileStorage storage = mock(DynamicFileStorage.class);
        when(storage.status()).thenReturn(new DynamicFileStorage.Status("local", List.of("local", "nas_mount"),
                localDirectory.toString(), true, nasDirectory.toString(), true));
        JdkSystemUsageProbe probe = new JdkSystemUsageProbe(storage);

        assertThat(probe.storageSpace()).isPresent();

        when(storage.status()).thenReturn(new DynamicFileStorage.Status("nas_mount", List.of("local", "nas_mount"),
                localDirectory.toString(), true, nasDirectory.toString(), true));

        Optional<Space> nasSpace = probe.storageSpace();

        assertThat(nasSpace).isPresent();
        assertThat(nasSpace.get().totalBytes()).isEqualTo(Files.getFileStore(nasDirectory).getTotalSpace());
    }

    @Test
    void storageProviderReflectsTheCurrentlyActiveProviderRatherThanAValueFixedAtConstruction() {
        SystemUsageProbe probe = new FakeProbe(OptionalDouble.of(0.1),
                OptionalLong.of(100L), OptionalLong.of(50L), Optional.of(new Space(100L, 50L)));
        DynamicFileStorage storage = mock(DynamicFileStorage.class);
        when(storage.status()).thenReturn(
                new DynamicFileStorage.Status("local", List.of("local", "nas_mount"), "/data/uploads", true, "/data/nas", false));
        SystemUsageSnapshotReader reader = new SystemUsageSnapshotReader(probe, storage);

        assertThat(reader.storageProvider()).isEqualTo("local");

        when(storage.status()).thenReturn(
                new DynamicFileStorage.Status("nas_mount", List.of("local", "nas_mount"), "/data/uploads", true, "/data/nas", true));

        assertThat(reader.storageProvider()).isEqualTo("nas_mount");
    }

    private record FakeProbe(OptionalDouble cpuLoad, OptionalLong totalMemoryBytes,
                             OptionalLong freeMemoryBytes, Optional<Space> storageSpace)
            implements SystemUsageProbe {
    }
}
