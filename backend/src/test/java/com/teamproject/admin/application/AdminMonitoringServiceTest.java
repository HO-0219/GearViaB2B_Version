package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.AdminMonitoringResponse;
import com.teamproject.common.config.RuntimeTuningProperties;
import com.teamproject.common.execution.ExecutorTelemetry;
import com.teamproject.aiusage.domain.AiUsageOperation;
import com.teamproject.aiusage.domain.AiUsageRecord;
import com.teamproject.aiusage.domain.AiUsageRecordRepository;
import com.teamproject.resource.storage.DynamicFileStorage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
class AdminMonitoringServiceTest {
    @Autowired private AiUsageRecordRepository records;

    @Test
    void usesKoreanMidnightForTodayAndMonthBoundaries() {
        records.save(AiUsageRecord.success(AiUsageOperation.ASSISTANT_RESPONSE, "gpt-a", 5L, 0L, 5L,
                LocalDateTime.of(2026, 8, 22, 14, 59, 59)));
        records.save(AiUsageRecord.success(AiUsageOperation.ASSISTANT_RESPONSE, "gpt-a", 7L, 0L, 7L,
                LocalDateTime.of(2026, 8, 22, 15, 0, 0)));
        records.save(AiUsageRecord.failure(AiUsageOperation.WEEKLY_REPORT, "gpt-b", "AI_REPORT_TIMEOUT",
                LocalDateTime.of(2026, 8, 22, 15, 1, 0)));
        records.save(AiUsageRecord.success(AiUsageOperation.DOCUMENT_EMBEDDING, "embed", 3L, 0L, 3L,
                LocalDateTime.of(2026, 7, 31, 14, 59, 59)));

        AdminMonitoringResponse response = service().overviewAt(Instant.parse("2026-08-23T00:30:00Z"));

        assertThat(response.aiUsage().timeZone()).isEqualTo("Asia/Seoul");
        assertThat(response.aiUsage().periods().today().requests()).isEqualTo(2L);
        assertThat(response.aiUsage().periods().today().failedRequests()).isEqualTo(1L);
        assertThat(response.aiUsage().periods().today().totalTokens()).isEqualTo(7L);
        assertThat(response.aiUsage().periods().thisMonth().requests()).isEqualTo(3L);
        assertThat(response.aiUsage().periods().allTime().requests()).isEqualTo(4L);
        assertThat(response.aiUsage().breakdown()).hasSize(3);
    }

    @Test
    void returnsPartialSystemAvailabilityWithoutDroppingOtherCards() {
        SystemUsageProbe probe = new FixedProbe(OptionalDouble.empty(), OptionalLong.of(800L),
                OptionalLong.of(200L), Optional.empty());
        AdminMonitoringResponse response = new AdminMonitoringService(records,
                new SystemUsageSnapshotReader(probe, storageWithProvider("nas_mount")),
                operationalTelemetry(), runtimeTuning())
                .overviewAt(Instant.parse("2026-08-23T00:30:00Z"));

        assertThat(response.system().cpu().available()).isFalse();
        assertThat(response.system().memory().usedBytes()).isEqualTo(600L);
        assertThat(response.system().storage().available()).isFalse();
        assertThat(response.system().storage().provider()).isEqualTo("nas_mount");
    }

    @Test
    void overviewIncludesBoundedRuntimeAndAlerts() {
        OperationalTelemetryReader telemetry = mock(OperationalTelemetryReader.class);
        when(telemetry.read()).thenReturn(new OperationalTelemetryReader.Snapshot(
                "backend-1", 1000,
                new OperationalTelemetryReader.DatabasePoolSnapshot(true, 18, 2, 20, 20),
                List.of(new OperationalTelemetryReader.DependencySnapshot("database", true),
                        new OperationalTelemetryReader.DependencySnapshot("storage", true)),
                List.of(new ExecutorTelemetry.ExecutorSnapshot("document-index", 2, 2, 1, 2,
                        95, 100, 50, 3))));
        SystemUsageProbe probe = new FixedProbe(OptionalDouble.of(0.25), OptionalLong.of(800L),
                OptionalLong.of(200L), Optional.of(new SystemUsageProbe.Space(1_000L, 400L)));

        AdminMonitoringResponse response = new AdminMonitoringService(records,
                new SystemUsageSnapshotReader(probe, storageWithProvider("local")), telemetry, runtimeTuning())
                .overviewAt(Instant.parse("2026-08-23T00:30:00Z"));

        assertThat(response.runtime().instanceId()).isEqualTo("backend-1");
        assertThat(response.runtime().maxTaskResults()).isEqualTo(1000);
        assertThat(response.databasePool().active()).isEqualTo(18);
        assertThat(response.executors()).extracting(value -> value.name())
                .containsExactly("document-index");
        assertThat(response.alerts()).extracting(value -> value.code())
                .contains("DATABASE_POOL_CRITICAL", "EXECUTOR_QUEUE_CRITICAL");
    }

    private AdminMonitoringService service() {
        SystemUsageProbe probe = new FixedProbe(OptionalDouble.of(0.25), OptionalLong.of(800L),
                OptionalLong.of(200L), Optional.of(new SystemUsageProbe.Space(1_000L, 400L)));
        return new AdminMonitoringService(records, new SystemUsageSnapshotReader(probe, storageWithProvider("local")),
                operationalTelemetry(), runtimeTuning());
    }

    private OperationalTelemetryReader operationalTelemetry() {
        OperationalTelemetryReader telemetry = mock(OperationalTelemetryReader.class);
        when(telemetry.read()).thenReturn(new OperationalTelemetryReader.Snapshot(
                "test-instance", 1000,
                new OperationalTelemetryReader.DatabasePoolSnapshot(false, 0, 0, 0, 20),
                List.of(), List.of()));
        return telemetry;
    }

    private RuntimeTuningProperties runtimeTuning() {
        return new RuntimeTuningProperties(
                new RuntimeTuningProperties.Database(20, 5, 30000),
                new RuntimeTuningProperties.Queries(1000),
                new RuntimeTuningProperties.Executors(
                        new RuntimeTuningProperties.Executor(1, 2, 100, 60),
                        new RuntimeTuningProperties.Executor(2, 4, 500, 60)),
                new RuntimeTuningProperties.Alerts(75, 90));
    }

    private DynamicFileStorage storageWithProvider(String provider) {
        DynamicFileStorage storage = mock(DynamicFileStorage.class);
        when(storage.status()).thenReturn(new DynamicFileStorage.Status(provider,
                List.of("local", "nas_mount"), "/opt/b2bgearvia/data/uploads", true,
                "/opt/b2bgearvia/data/nas", false));
        return storage;
    }

    private record FixedProbe(OptionalDouble cpuLoad, OptionalLong totalMemoryBytes,
                              OptionalLong freeMemoryBytes, Optional<SystemUsageProbe.Space> storageSpace)
            implements SystemUsageProbe {
    }
}
