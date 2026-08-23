package com.teamproject.admin.application;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JdkSystemUsageProbe implements SystemUsageProbe {
    private final java.lang.management.OperatingSystemMXBean operatingSystem;
    private final String storageProvider;
    private final String localRoot;
    private final String nasRoot;

    @Autowired
    public JdkSystemUsageProbe(@Value("${app.storage.provider:local}") String storageProvider,
                               @Value("${app.storage.local-root:/opt/b2bgearvia/data/uploads}") String localRoot,
                               @Value("${app.storage.nas-root:}") String nasRoot) {
        this(ManagementFactory.getOperatingSystemMXBean(), storageProvider, localRoot, nasRoot);
    }

    JdkSystemUsageProbe(java.lang.management.OperatingSystemMXBean operatingSystem, String storageProvider,
                        String localRoot, String nasRoot) {
        this.operatingSystem = operatingSystem;
        this.storageProvider = storageProvider;
        this.localRoot = localRoot;
        this.nasRoot = nasRoot;
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
        String root = "nas_mount".equals(storageProvider) ? nasRoot : localRoot;
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
