package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.AdminMonitoringResponse;
import com.teamproject.admin.application.dto.AdminDtos.AiUsageBreakdownResponse;
import com.teamproject.admin.application.dto.AdminDtos.AiUsagePeriodResponse;
import com.teamproject.admin.application.dto.AdminDtos.AiUsagePeriodsResponse;
import com.teamproject.admin.application.dto.AdminDtos.AiUsageResponse;
import com.teamproject.admin.application.dto.AdminDtos.CapacityResponse;
import com.teamproject.admin.application.dto.AdminDtos.MetricResponse;
import com.teamproject.admin.application.dto.AdminDtos.SystemUsageResponse;
import com.teamproject.aiusage.domain.AiUsageBreakdown;
import com.teamproject.aiusage.domain.AiUsageRecordRepository;
import com.teamproject.aiusage.domain.AiUsageTotals;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMonitoringService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final AiUsageRecordRepository usageRecords;
    private final SystemUsageSnapshotReader systemUsage;

    public AdminMonitoringService(AiUsageRecordRepository usageRecords, SystemUsageSnapshotReader systemUsage) {
        this.usageRecords = usageRecords;
        this.systemUsage = systemUsage;
    }

    @Transactional(readOnly = true)
    public AdminMonitoringResponse overview() {
        return overviewAt(Instant.now());
    }

    @Transactional(readOnly = true)
    AdminMonitoringResponse overviewAt(Instant now) {
        return new AdminMonitoringResponse(system(), aiUsage(now));
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
