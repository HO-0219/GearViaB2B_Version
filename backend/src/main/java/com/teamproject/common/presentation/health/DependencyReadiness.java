package com.teamproject.common.presentation.health;

import com.teamproject.resource.storage.DynamicFileStorage;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class DependencyReadiness {
    // A stale NFS/SMB mount makes a plain filesystem probe block indefinitely; run
    // it on a side thread so readiness reports "down" instead of hanging the request.
    private static final int STORAGE_PROBE_TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;
    private final DynamicFileStorage storage;
    private final ExecutorService storageProbePool = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "readiness-storage-probe");
        thread.setDaemon(true);
        return thread;
    });

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
        Future<DynamicFileStorage.ActiveHealth> probe = storageProbePool.submit(storage::activeHealth);
        try {
            DynamicFileStorage.ActiveHealth health = probe.get(STORAGE_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new Component("storage", health.up(), health.provider());
        } catch (TimeoutException timeout) {
            probe.cancel(true);
            return new Component("storage", false, "timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Component("storage", false, "unavailable");
        } catch (java.util.concurrent.ExecutionException failed) {
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
