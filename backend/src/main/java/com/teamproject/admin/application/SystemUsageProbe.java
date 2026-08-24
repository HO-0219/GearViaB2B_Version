package com.teamproject.admin.application;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

public interface SystemUsageProbe {
    OptionalDouble cpuLoad();

    OptionalLong totalMemoryBytes();

    OptionalLong freeMemoryBytes();

    Optional<Space> storageSpace();

    record Space(long totalBytes, long usableBytes) {
    }
}
