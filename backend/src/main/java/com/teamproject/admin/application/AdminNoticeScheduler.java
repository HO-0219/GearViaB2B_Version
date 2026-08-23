package com.teamproject.admin.application;

import com.teamproject.common.scheduling.DatabaseJobLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;

/** Ticks every minute and asks {@link AdminNoticeService} to deliver whatever notices are due. */
@Component
public class AdminNoticeScheduler {
    private final AdminNoticeService notices;
    private final DatabaseJobLock jobLock;

    public AdminNoticeScheduler(AdminNoticeService notices, DatabaseJobLock jobLock) {
        this.notices = notices;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${app.notification.admin-notice-cron:0 * * * * *}")
    public void deliverDueNotices() {
        if (!jobLock.acquire("admin-notice-delivery", Duration.ofMinutes(2))) return;
        notices.deliverDue();
    }
}
