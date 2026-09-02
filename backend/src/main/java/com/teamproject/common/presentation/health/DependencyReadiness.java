package com.teamproject.common.presentation.health;

import com.teamproject.resource.storage.DynamicFileStorage;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

@Component
public class DependencyReadiness {
    private final DataSource dataSource;
    private final DynamicFileStorage storage;

    public DependencyReadiness(DataSource dataSource, DynamicFileStorage storage) {
        this.dataSource = dataSource;
        this.storage = storage;
    }

    public ReadinessSnapshot check() {
        Component database = databaseHealth();
        Component activeStorage = storageHealth();
        return new ReadinessSnapshot(database.up() && activeStorage.up(), List.of(database, activeStorage));
    }

    private Component databaseHealth() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return new Component("database", valid, valid ? "available" : "unavailable");
        } catch (Exception ignored) {
            return new Component("database", false, "unavailable");
        }
    }

    private Component storageHealth() {
        try {
            DynamicFileStorage.ActiveHealth health = storage.activeHealth();
            return new Component("storage", health.up(), health.provider());
        } catch (RuntimeException ignored) {
            return new Component("storage", false, "unavailable");
        }
    }

    public record ReadinessSnapshot(boolean up, List<Component> components) {
        public ReadinessSnapshot {
            components = List.copyOf(components);
        }

        public static ReadinessSnapshot down(String component, String detail) {
            return new ReadinessSnapshot(false, List.of(new Component(component, false, detail)));
        }
    }

    public record Component(String name, boolean up, String detail) {}
}
