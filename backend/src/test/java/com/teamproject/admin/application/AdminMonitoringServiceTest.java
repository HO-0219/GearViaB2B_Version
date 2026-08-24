package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.AdminMonitoringResponse;
import com.teamproject.aiusage.domain.AiUsageOperation;
import com.teamproject.aiusage.domain.AiUsageRecord;
import com.teamproject.aiusage.domain.AiUsageRecordRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

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
                new SystemUsageSnapshotReader(probe, "nas_mount"))
                .overviewAt(Instant.parse("2026-08-23T00:30:00Z"));

        assertThat(response.system().cpu().available()).isFalse();
        assertThat(response.system().memory().usedBytes()).isEqualTo(600L);
        assertThat(response.system().storage().available()).isFalse();
        assertThat(response.system().storage().provider()).isEqualTo("nas_mount");
    }

    private AdminMonitoringService service() {
        SystemUsageProbe probe = new FixedProbe(OptionalDouble.of(0.25), OptionalLong.of(800L),
                OptionalLong.of(200L), Optional.of(new SystemUsageProbe.Space(1_000L, 400L)));
        return new AdminMonitoringService(records, new SystemUsageSnapshotReader(probe, "local"));
    }

    private record FixedProbe(OptionalDouble cpuLoad, OptionalLong totalMemoryBytes,
                              OptionalLong freeMemoryBytes, Optional<SystemUsageProbe.Space> storageSpace)
            implements SystemUsageProbe {
    }
}
