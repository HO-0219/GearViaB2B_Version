package com.teamproject.common.scheduling;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class DatabaseJobLock {
    private static final LocalDateTime RELEASED = LocalDateTime.of(1970, 1, 1, 0, 0);
    private final JdbcTemplate jdbc;
    private final String owner = ManagementFactory.getRuntimeMXBean().getName();
    public DatabaseJobLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean acquire(String name, Duration lease) {
        jdbc.update("INSERT IGNORE INTO scheduled_job_locks (name, locked_until) VALUES (?, ?)",
                name, RELEASED);
        LocalDateTime now = LocalDateTime.now();
        return jdbc.update("""
                UPDATE scheduled_job_locks
                SET locked_until = ?, locked_at = ?, locked_by = ?
                WHERE name = ? AND locked_until <= ?
                """, now.plus(lease), now, owner, name, now) == 1;
    }
}
