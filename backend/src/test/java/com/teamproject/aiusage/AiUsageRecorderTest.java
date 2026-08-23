package com.teamproject.aiusage;

import com.teamproject.aiusage.application.AiUsageRecordWriter;
import com.teamproject.aiusage.application.AiUsageRecorder;
import com.teamproject.aiusage.domain.AiUsageOperation;
import com.teamproject.aiusage.domain.AiUsageRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class AiUsageRecorderTest {
    @Autowired private TransactionTemplate transactions;
    @Autowired private AiUsageRecorder recorder;
    @Autowired private AiUsageRecordRepository records;

    @Test
    void preservesUsageWhenTheCallerTransactionRollsBack() {
        transactions.executeWithoutResult(status -> {
            recorder.success(AiUsageOperation.WEEKLY_REPORT, "gpt-5.6-luna", 10L, 2L, 12L);
            status.setRollbackOnly();
        });

        assertThat(records.findAll()).hasSize(1);
    }

    @Test
    void ignoresUsagePersistenceFailuresWithoutSkippingTheRecordAttempt() {
        AiUsageRecordWriter writer = mock(AiUsageRecordWriter.class);
        doThrow(new DataIntegrityViolationException("test persistence failure"))
                .when(writer).write(any());
        AiUsageRecorder failedRecorder = new AiUsageRecorder(writer);

        assertThatCode(() -> {
            failedRecorder.success(AiUsageOperation.ASSISTANT_RESPONSE, "gpt-5.6-luna", 10L, 2L, 12L);
            failedRecorder.failure(AiUsageOperation.DOCUMENT_EMBEDDING,
                    "text-embedding-3-small", "AI_ASSISTANT_EMBEDDING_FAILED");
        }).doesNotThrowAnyException();

        verify(writer, times(2)).write(any());
    }
}
