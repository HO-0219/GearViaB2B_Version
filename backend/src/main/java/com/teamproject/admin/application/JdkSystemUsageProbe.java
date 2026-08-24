package com.teamproject.admin.application;

import com.teamproject.resource.storage.DynamicFileStorage;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JdkSystemUsageProbe implements SystemUsageProbe {
    private final java.lang.management.OperatingSystemMXBean operatingSystem;
    private final DynamicFileStorage storage;

    @Autowired
    public JdkSystemUsageProbe(DynamicFileStorage storage) {
        this(ManagementFactory.getOperatingSystemMXBean(), storage);
    }

    JdkSystemUsageProbe(java.lang.management.OperatingSystemMXBean operatingSystem, DynamicFileStorage storage) {
        this.operatingSystem = operatingSystem;
        this.storage = storage;
    }

    @Override
    public OptionalDouble cpuLoad() {
        if (operatingSystem instanceof com.sun.management.OperatingSystemMXBean extended) {
            return OptionalDouble.of(extended.getCpuLoad());
        }
        return OptionalDouble.empty();
    }

    @Override
    public OptionalLong totalMemoryBytes() {
        if (operatingSystem instanceof com.sun.management.OperatingSystemMXBean extended) {
            return OptionalLong.of(extended.getTotalMemorySize());
        }
        return OptionalLong.empty();
    }

    @Override
    public OptionalLong freeMemoryBytes() {
        if (operatingSystem instanceof com.sun.management.OperatingSystemMXBean extended) {
            return OptionalLong.of(extended.getFreeMemorySize());
        }
        return OptionalLong.empty();
    }

    @Override
    public Optional<Space> storageSpace() {
        DynamicFileStorage.Status status = storage.status();
        String root = "nas_mount".equals(status.provider()) ? status.nasRootPath() : status.localRootPath();
        if (root == null || root.isBlank()) {
            return Optional.empty();
        }

        try {
            Path path = Path.of(root).toAbsolutePath().normalize();
            if (!Files.isDirectory(path) || !Files.isReadable(path)) {
                return Optional.empty();
            }
            var fileStore = Files.getFileStore(path);
            return Optional.of(new Space(fileStore.getTotalSpace(), fileStore.getUsableSpace()));
        } catch (IOException | InvalidPathException | SecurityException ex) {
            return Optional.empty();
        }
    }
}
