package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.AdminMonitoringResponse;
import com.teamproject.admin.application.dto.AdminDtos.AlertResponse;
import com.teamproject.admin.application.dto.AdminDtos.AiUsageBreakdownResponse;
import com.teamproject.admin.application.dto.AdminDtos.AiUsagePeriodResponse;
import com.teamproject.admin.application.dto.AdminDtos.AiUsagePeriodsResponse;
import com.teamproject.admin.application.dto.AdminDtos.AiUsageResponse;
import com.teamproject.admin.application.dto.AdminDtos.CapacityResponse;
import com.teamproject.admin.application.dto.AdminDtos.MetricResponse;
import com.teamproject.admin.application.dto.AdminDtos.SystemUsageResponse;
import com.teamproject.admin.application.dto.AdminDtos.DatabasePoolResponse;
import com.teamproject.admin.application.dto.AdminDtos.DependencyResponse;
import com.teamproject.admin.application.dto.AdminDtos.ExecutorResponse;
import com.teamproject.admin.application.dto.AdminDtos.RuntimeResponse;
import com.teamproject.common.config.RuntimeTuningProperties;
import com.teamproject.common.execution.ExecutorTelemetry;
import com.teamproject.aiusage.domain.AiUsageBreakdown;
import com.teamproject.aiusage.domain.AiUsageRecordRepository;
import com.teamproject.aiusage.domain.AiUsageTotals;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

//AdminMonitoringService
@Service
public class AdminMonitoringService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final AiUsageRecordRepository usageRecords;
    private final SystemUsageSnapshotReader systemUsage;
    private final OperationalTelemetryReader operationalTelemetry;
    private final RuntimeTuningProperties runtimeTuning;

    public AdminMonitoringService(AiUsageRecordRepository usageRecords, SystemUsageSnapshotReader systemUsage,
            OperationalTelemetryReader operationalTelemetry, RuntimeTuningProperties runtimeTuning) {
        this.usageRecords = usageRecords;
        this.systemUsage = systemUsage;
        this.operationalTelemetry = operationalTelemetry;
        this.runtimeTuning = runtimeTuning;
    }

    @Transactional(readOnly = true)
    public AdminMonitoringResponse overview() {
        return overviewAt(Instant.now());
    }

    @Transactional(readOnly = true)
    AdminMonitoringResponse overviewAt(Instant now) {
        SystemUsageResponse system = system();
        OperationalTelemetryReader.Snapshot operational = operationalTelemetry.read();
        DatabasePoolResponse pool = pool(operational.databasePool());
        List<DependencyResponse> dependencies = operational.dependencies().stream()
                .map(value -> new DependencyResponse(value.name(), value.up() ? "UP" : "DOWN"))
                .toList();
        List<ExecutorResponse> executors = operational.executors().stream().map(this::executor).toList();
        return new AdminMonitoringResponse(system, aiUsage(now),
                new RuntimeResponse(operational.instanceId(), operational.maxTaskResults()),
                pool, dependencies, executors, alerts(system, pool, dependencies, executors));
    }

    private DatabasePoolResponse pool(OperationalTelemetryReader.DatabasePoolSnapshot value) {
        return new DatabasePoolResponse(value.available(), value.active(), value.idle(), value.total(),
                value.maximum(), percent(value.active(), value.maximum()));
    }

    private ExecutorResponse executor(ExecutorTelemetry.ExecutorSnapshot value) {
        return new ExecutorResponse(value.name(), value.active(), value.poolSize(), value.maxSize(),
                value.queueSize(), value.queueCapacity(), percent(value.queueSize(), value.queueCapacity()),
                value.completed(), value.rejected());
    }

    private List<AlertResponse> alerts(SystemUsageResponse system, DatabasePoolResponse pool,
            List<DependencyResponse> dependencies, List<ExecutorResponse> executors) {
        List<AlertResponse> alerts = new ArrayList<>();
        addThresholdAlert(alerts, "CPU", system.cpu().available() ? system.cpu().usedPercent() : null, "cpu");
        addThresholdAlert(alerts, "MEMORY", system.memory().available() ? system.memory().usedPercent() : null,
                "memory");
        addThresholdAlert(alerts, "STORAGE", system.storage().available() ? system.storage().usedPercent() : null,
                system.storage().provider());
        addThresholdAlert(alerts, "DATABASE_POOL", pool.available() ? pool.usedPercent() : null, "database");
        for (ExecutorResponse executor : executors) {
            addThresholdAlert(alerts, "EXECUTOR_QUEUE", executor.queueUsedPercent(), executor.name());
        }
        for (DependencyResponse dependency : dependencies) {
            if ("DOWN".equals(dependency.status())) {
                alerts.add(new AlertResponse(dependency.name().toUpperCase() + "_UNAVAILABLE",
                        "CRITICAL", null, dependency.name()));
            }
        }
        return List.copyOf(alerts);
    }

    private void addThresholdAlert(List<AlertResponse> alerts, String resource, Double usedPercent,
            String subject) {
        if (usedPercent == null || usedPercent < runtimeTuning.alerts().warningPercent()) return;
        String severity = usedPercent >= runtimeTuning.alerts().criticalPercent() ? "CRITICAL" : "WARNING";
        alerts.add(new AlertResponse(resource + "_" + severity, severity, usedPercent, subject));
    }

    private Double percent(long used, long capacity) {
        return capacity <= 0 ? null : used * 100.0 / capacity;
    }

    private SystemUsageResponse system() {
        SystemUsageSnapshotReader.Snapshot snapshot = systemUsage.read();
        return new SystemUsageResponse(
                new MetricResponse(snapshot.cpu().available(), snapshot.cpu().usedPercent()),
                capacity(snapshot.memory(), null),
                capacity(snapshot.storage(), systemUsage.storageProvider()));
    }

    private AiUsageResponse aiUsage(Instant now) {
        return new AiUsageResponse(SEOUL.getId(), new AiUsagePeriodsResponse(
                period(usageRecords.totalsSince(utcStartOfSeoulDay(now))),
                period(usageRecords.totalsSince(utcStartOfSeoulMonth(now))),
                period(usageRecords.allTimeTotals())), usageRecords.breakdown().stream()
                .map(this::breakdown)
                .toList());
    }

    private CapacityResponse capacity(SystemUsageSnapshotReader.Capacity value, String provider) {
        return new CapacityResponse(value.available(), value.usedBytes(), value.totalBytes(), value.usedPercent(), provider);
    }

    private AiUsagePeriodResponse period(AiUsageTotals value) {
        return new AiUsagePeriodResponse(value.requests(), value.failedRequests(), value.inputTokens(),
                value.outputTokens(), value.totalTokens());
    }

    private AiUsageBreakdownResponse breakdown(AiUsageBreakdown value) {
        return new AiUsageBreakdownResponse(value.operation().name(), value.model(), value.requests(),
                value.failedRequests(), value.inputTokens(), value.outputTokens(), value.totalTokens());
    }

    private static LocalDateTime utcStartOfSeoulDay(Instant now) {
        LocalDate date = now.atZone(SEOUL).toLocalDate();
        return utc(date);
    }

    private static LocalDateTime utcStartOfSeoulMonth(Instant now) {
        LocalDate month = now.atZone(SEOUL).toLocalDate().withDayOfMonth(1);
        return utc(month);
    }

    private static LocalDateTime utc(LocalDate date) {
        return LocalDateTime.ofInstant(date.atStartOfDay(SEOUL).toInstant(), ZoneOffset.UTC);
    }
}
