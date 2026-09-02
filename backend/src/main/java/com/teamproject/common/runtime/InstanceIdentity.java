package com.teamproject.common.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public final class InstanceIdentity {
    private final String value;

    public InstanceIdentity(@Value("${app.instance-id:}") String configuredValue) {
        String normalized = configuredValue == null ? "" : configuredValue.trim();
        this.value = normalized.isEmpty() ? UUID.randomUUID().toString() : normalized;
    }

    public String value() {
        return value;
    }
}
