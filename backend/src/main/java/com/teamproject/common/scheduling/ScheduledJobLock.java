package com.teamproject.common.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduled_job_locks")
class ScheduledJobLock {
    @Id
    @Column(length = 80)
    private String name;
    @Column(nullable = false)
    private LocalDateTime lockedUntil;
    private LocalDateTime lockedAt;
    @Column(length = 120)
    private String lockedBy;

    protected ScheduledJobLock() {}
}
