package com.teamproject.aiusage.application;

import com.teamproject.aiusage.domain.AiUsageRecord;
import com.teamproject.aiusage.domain.AiUsageRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiUsageRecordWriter {
    private final AiUsageRecordRepository records;

    public AiUsageRecordWriter(AiUsageRecordRepository records) {
        this.records = records;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AiUsageRecord value) {
        records.saveAndFlush(value);
    }
}
