package com.teamproject.aiusage;

import com.teamproject.aiusage.domain.AiUsageOperation;
import com.teamproject.aiusage.domain.AiUsageOutcome;
import com.teamproject.aiusage.domain.AiUsageRecord;
import com.teamproject.aiusage.domain.AiUsageRecordRepository;
import com.teamproject.aiusage.domain.AiUsageBreakdown;
import com.teamproject.aiusage.domain.AiUsageTotals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AiUsageRecordRepositoryTest {
    @Autowired private AiUsageRecordRepository records;

    @Test
    void storesOnlyUsageMetadataForSuccessfulAndFailedAttempts() {
        LocalDateTime at = LocalDateTime.of(2026, 8, 23, 0, 15);
        records.save(AiUsageRecord.success(AiUsageOperation.ASSISTANT_RESPONSE,
                "gpt-5.6-luna", 12L, 8L, 20L, at));
        records.save(AiUsageRecord.failure(AiUsageOperation.DOCUMENT_EMBEDDING,
                "text-embedding-3-small", "AI_ASSISTANT_EMBEDDING_FAILED", at.plusSeconds(1)));

        List<AiUsageRecord> saved = records.findAllByOrderByOccurredAtAsc();

        assertThat(saved).extracting(AiUsageRecord::getOutcome)
                .containsExactly(AiUsageOutcome.SUCCESS, AiUsageOutcome.FAILURE);
        assertThat(saved.get(0).getTotalTokens()).isEqualTo(20L);
        assertThat(saved.get(1).getFailureCode()).isEqualTo("AI_ASSISTANT_EMBEDDING_FAILED");
    }

    @Test
    void aggregatesKnownTokensAndGroupsAttemptsByOperationAndModel() {
        LocalDateTime at = LocalDateTime.of(2026, 8, 23, 0, 15);
        records.save(AiUsageRecord.success(AiUsageOperation.ASSISTANT_RESPONSE,
                "gpt-5.6-luna", 12L, 8L, 20L, at));
        records.save(AiUsageRecord.failure(AiUsageOperation.ASSISTANT_RESPONSE,
                "gpt-5.6-luna", "AI_ASSISTANT_RESPONSE_FAILED", at.plusSeconds(1)));
        records.save(AiUsageRecord.success(AiUsageOperation.WEEKLY_REPORT,
                "gpt-5.6-luna", 20L, 10L, 30L, at.minusDays(1)));

        AiUsageTotals totals = records.totalsSince(at);

        assertThat(totals).isEqualTo(new AiUsageTotals(2L, 1L, 12L, 8L, 20L));
        assertThat(records.breakdown()).containsExactly(
                new AiUsageBreakdown(AiUsageOperation.ASSISTANT_RESPONSE, "gpt-5.6-luna",
                        2L, 1L, 12L, 8L, 20L),
                new AiUsageBreakdown(AiUsageOperation.WEEKLY_REPORT, "gpt-5.6-luna",
                        1L, 0L, 20L, 10L, 30L));
    }
}
