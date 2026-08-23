package com.teamproject.admin.application;

import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SystemUsageSnapshotReader {
    private final SystemUsageProbe probe;
    private final String storageProvider;

    public SystemUsageSnapshotReader(SystemUsageProbe probe,
                                     @Value("${app.storage.provider:local}") String storageProvider) {
        this.probe = probe;
        this.storageProvider = storageProvider;
    }

    public Snapshot read() {
        return new Snapshot(cpu(), memory(), storage());
    }

    public String storageProvider() {
        return storageProvider;
    }

    private Metric cpu() {
        OptionalDouble load = probe.cpuLoad();
        if (load.isEmpty() || load.getAsDouble() < 0.0) {
            return new Metric(false, null);
        }
        return new Metric(true, load.getAsDouble() * 100.0);
    }

    private Capacity memory() {
        OptionalLong total = probe.totalMemoryBytes();
        OptionalLong free = probe.freeMemoryBytes();
        if (total.isEmpty() || free.isEmpty()) {
            return unavailableCapacity();
        }
        return capacity(total.getAsLong(), free.getAsLong());
    }

    private Capacity storage() {
        return probe.storageSpace()
                .map(space -> capacity(space.totalBytes(), space.usableBytes()))
                .orElseGet(SystemUsageSnapshotReader::unavailableCapacity);
    }

    private static Capacity capacity(long totalBytes, long remainingBytes) {
        if (totalBytes <= 0 || remainingBytes < 0 || remainingBytes > totalBytes) {
            return unavailableCapacity();
        }
        long usedBytes = totalBytes - remainingBytes;
        return new Capacity(true, usedBytes, totalBytes, usedBytes * 100.0 / totalBytes);
    }

    private static Capacity unavailableCapacity() {
        return new Capacity(false, null, null, null);
    }

    public record Snapshot(Metric cpu, Capacity memory, Capacity storage) {
    }

    public record Metric(boolean available, Double usedPercent) {
    }

    public record Capacity(boolean available, Long usedBytes, Long totalBytes, Double usedPercent) {
    }
}
