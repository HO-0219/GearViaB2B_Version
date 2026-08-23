package com.teamproject.aiusage.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AiUsageRecordRepository extends JpaRepository<AiUsageRecord, Long> {
    List<AiUsageRecord> findAllByOrderByOccurredAtAsc();

    @Query("""
            select new com.teamproject.aiusage.domain.AiUsageTotals(
                count(record),
                coalesce(sum(case when record.outcome = com.teamproject.aiusage.domain.AiUsageOutcome.FAILURE then 1 else 0 end), 0),
                sum(record.inputTokens), sum(record.outputTokens), sum(record.totalTokens)
            )
            from AiUsageRecord record
            where record.occurredAt >= :from
            """)
    AiUsageTotals totalsSince(LocalDateTime from);

    @Query("""
            select new com.teamproject.aiusage.domain.AiUsageTotals(
                count(record),
                coalesce(sum(case when record.outcome = com.teamproject.aiusage.domain.AiUsageOutcome.FAILURE then 1 else 0 end), 0),
                sum(record.inputTokens), sum(record.outputTokens), sum(record.totalTokens)
            )
            from AiUsageRecord record
            """)
    AiUsageTotals allTimeTotals();

    @Query("""
            select new com.teamproject.aiusage.domain.AiUsageBreakdown(
                record.operation, record.model, count(record),
                coalesce(sum(case when record.outcome = com.teamproject.aiusage.domain.AiUsageOutcome.FAILURE then 1 else 0 end), 0),
                sum(record.inputTokens), sum(record.outputTokens), sum(record.totalTokens)
            )
            from AiUsageRecord record
            group by record.operation, record.model
            order by record.operation, record.model
            """)
    List<AiUsageBreakdown> breakdown();
}
