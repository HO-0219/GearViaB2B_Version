package com.teamproject.notification.application;

import com.teamproject.common.scheduling.DatabaseJobLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class TaskReminderScheduler {
    private final TaskReminderBatchService batches;
    private final DatabaseJobLock jobLock;

    public TaskReminderScheduler(TaskReminderBatchService batches, DatabaseJobLock jobLock) {
        this.batches = batches;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${app.notification.due-soon-cron:0 */15 * * * *}", zone = "UTC")
    public void sendDueSoonNotifications() {
        if (!jobLock.acquire("task-due-reminder", Duration.ofMinutes(10))) return;
        LocalDateTime utcNow = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime from = utcNow.minusHours(13);
        LocalDateTime to = utcNow.plusHours(39);
        TaskReminderBatchService.Cursor cursor = null;
        do {
            cursor = batches.process(from, to, cursor);
        } while (cursor != null && cursor.hasMore());
    }
}
