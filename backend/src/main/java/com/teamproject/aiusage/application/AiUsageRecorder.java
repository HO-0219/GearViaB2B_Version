package com.teamproject.aiusage.application;

import com.teamproject.aiusage.domain.AiUsageOperation;
import com.teamproject.aiusage.domain.AiUsageRecord;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AiUsageRecorder {
    private static final Logger log = LoggerFactory.getLogger(AiUsageRecorder.class);

    private final AiUsageRecordWriter writer;

    public AiUsageRecorder(AiUsageRecordWriter writer) {
        this.writer = writer;
    }

    public void success(AiUsageOperation operation, String model, Long inputTokens,
                        Long outputTokens, Long totalTokens) {
        safely(() -> writer.write(AiUsageRecord.success(operation, model, inputTokens,
                outputTokens, totalTokens, utcNow())));
    }

    public void failure(AiUsageOperation operation, String model, String failureCode) {
        safely(() -> writer.write(AiUsageRecord.failure(operation, model, failureCode, utcNow())));
    }

    private void safely(Runnable write) {
        try {
            write.run();
        } catch (Exception exception) {
            log.warn("AI usage record persistence failed: exception={}",
                    exception.getClass().getSimpleName());
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
