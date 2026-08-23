package com.teamproject.admin.application;

import com.teamproject.admin.application.SystemUsageProbe.Space;
import com.teamproject.admin.application.SystemUsageSnapshotReader.Capacity;
import com.teamproject.admin.application.SystemUsageSnapshotReader.Metric;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SystemUsageSnapshotReaderTest {
    @Test
    void convertsAvailableProbeValuesToCurrentUsageCards() {
        SystemUsageProbe probe = new FakeProbe(OptionalDouble.of(0.25),
                OptionalLong.of(800L), OptionalLong.of(200L),
                Optional.of(new Space(1_000L, 400L)));

        SystemUsageSnapshotReader.Snapshot snapshot = new SystemUsageSnapshotReader(probe, "local").read();

        assertThat(snapshot.cpu()).isEqualTo(new Metric(true, 25.0));
        assertThat(snapshot.memory()).isEqualTo(new Capacity(true, 600L, 800L, 75.0));
        assertThat(snapshot.storage()).isEqualTo(new Capacity(true, 600L, 1_000L, 60.0));
    }

    @Test
    void keepsMemoryAndStorageVisibleWhenCpuLoadIsUnavailable() {
        SystemUsageProbe probe = new FakeProbe(OptionalDouble.empty(),
                OptionalLong.of(800L), OptionalLong.of(200L),
                Optional.of(new Space(1_000L, 400L)));

        SystemUsageSnapshotReader.Snapshot snapshot = new SystemUsageSnapshotReader(probe, "local").read();

        assertThat(snapshot.cpu().available()).isFalse();
        assertThat(snapshot.memory().usedBytes()).isEqualTo(600L);
        assertThat(snapshot.storage().usedBytes()).isEqualTo(600L);
    }

    @Test
    void marksOnlyInvalidOrMissingProbeValuesAsUnavailable() {
        SystemUsageProbe probe = new FakeProbe(OptionalDouble.of(-1.0),
                OptionalLong.of(100L), OptionalLong.of(200L), Optional.empty());

        SystemUsageSnapshotReader.Snapshot snapshot = new SystemUsageSnapshotReader(probe, "nas_mount").read();

        assertThat(snapshot.cpu()).isEqualTo(new Metric(false, null));
        assertThat(snapshot.memory()).isEqualTo(new Capacity(false, null, null, null));
        assertThat(snapshot.storage()).isEqualTo(new Capacity(false, null, null, null));
    }

    @Test
    void treatsAnInaccessibleNasMountAsUnavailable(@TempDir Path temporaryDirectory) {
        JdkSystemUsageProbe probe = new JdkSystemUsageProbe("nas_mount", temporaryDirectory.toString(),
                temporaryDirectory.resolve("missing-nas-mount").toString());

        assertThat(probe.storageSpace()).isEmpty();
    }

    @Test
    void treatsAnInvalidStorageRootAsUnavailable() {
        JdkSystemUsageProbe probe = new JdkSystemUsageProbe("local", "\u0000", "");

        assertThat(probe.storageSpace()).isEmpty();
    }

    private record FakeProbe(OptionalDouble cpuLoad, OptionalLong totalMemoryBytes,
                             OptionalLong freeMemoryBytes, Optional<Space> storageSpace)
            implements SystemUsageProbe {
    }
}
