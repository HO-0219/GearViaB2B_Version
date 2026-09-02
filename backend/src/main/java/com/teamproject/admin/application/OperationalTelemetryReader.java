package com.teamproject.admin.application;

import com.teamproject.common.config.RuntimeTuningProperties;
import com.teamproject.common.execution.ExecutorTelemetry;
import com.teamproject.common.presentation.health.DependencyReadiness;
import com.teamproject.common.runtime.InstanceIdentity;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

@Component
public class OperationalTelemetryReader {
    private final InstanceIdentity identity;
    private final RuntimeTuningProperties runtime;
    private final DataSource dataSource;
    private final DependencyReadiness readiness;
    private final ExecutorTelemetry executors;

    public OperationalTelemetryReader(InstanceIdentity identity, RuntimeTuningProperties runtime,
            DataSource dataSource, DependencyReadiness readiness, ExecutorTelemetry executors) {
        this.identity = identity;
        this.runtime = runtime;
        this.dataSource = dataSource;
        this.readiness = readiness;
        this.executors = executors;
    }

    public Snapshot read() {
        return new Snapshot(identity.value(), runtime.queries().maxTaskResults(), databasePool(),
                readiness.check().components().stream()
                        .map(component -> new DependencySnapshot(component.name(), component.up()))
                        .toList(), executors.snapshots());
    }

    private DatabasePoolSnapshot databasePool() {
        int configuredMaximum = runtime.database().maximumPoolSize();
        try {
            HikariDataSource hikari = dataSource instanceof HikariDataSource value
                    ? value : dataSource.unwrap(HikariDataSource.class);
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool == null) {
                return new DatabasePoolSnapshot(false, 0, 0, 0, configuredMaximum);
            }
            return new DatabasePoolSnapshot(true, pool.getActiveConnections(), pool.getIdleConnections(),
                    pool.getTotalConnections(), hikari.getMaximumPoolSize());
        } catch (Exception ignored) {
            return new DatabasePoolSnapshot(false, 0, 0, 0, configuredMaximum);
        }
    }

    public record Snapshot(String instanceId, int maxTaskResults, DatabasePoolSnapshot databasePool,
            List<DependencySnapshot> dependencies, List<ExecutorTelemetry.ExecutorSnapshot> executors) {
        public Snapshot {
            dependencies = List.copyOf(dependencies);
            executors = List.copyOf(executors);
        }
    }

    public record DatabasePoolSnapshot(boolean available, int active, int idle, int total, int maximum) {}
    public record DependencySnapshot(String name, boolean up) {}
}
