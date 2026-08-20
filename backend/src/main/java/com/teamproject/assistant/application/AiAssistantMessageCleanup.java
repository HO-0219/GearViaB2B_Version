package com.teamproject.assistant.application;

import com.teamproject.assistant.domain.AiAssistantMessageRepository;
import com.teamproject.common.scheduling.DatabaseJobLock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AiAssistantMessageCleanup {
    private final AiAssistantMessageRepository messages;
    private final DatabaseJobLock locks;
    private final int retentionDays;

    public AiAssistantMessageCleanup(AiAssistantMessageRepository messages, DatabaseJobLock locks,
            @Value("${app.ai-assistant.message-retention-days:90}") int retentionDays) {
        this.messages = messages;
        this.locks = locks;
        this.retentionDays = Math.max(7, retentionDays);
    }

    @Scheduled(cron = "${app.ai-assistant.message-cleanup-cron:0 35 4 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void cleanup() {
        if (!locks.acquire("ai-assistant-message-cleanup", Duration.ofMinutes(30))) return;
        messages.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(retentionDays));
    }
}
