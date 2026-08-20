package com.teamproject.notification.application;

import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskReminderBatchService {
    private static final int BATCH_SIZE = 200;
    private static final EnumSet<Task.Status> ACTIVE_STATUSES = EnumSet.of(
            Task.Status.REQUESTED, Task.Status.TODO, Task.Status.IN_PROGRESS, Task.Status.ON_HOLD);
    private final TaskRepository tasks;
    private final NotificationService notifications;

    public TaskReminderBatchService(TaskRepository tasks, NotificationService notifications) {
        this.tasks = tasks;
        this.notifications = notifications;
    }

    @Transactional
    public Cursor process(LocalDateTime from, LocalDateTime to, Cursor cursor) {
        var page = cursor == null
                ? tasks.findDueReminderBatch(ACTIVE_STATUSES, from, to, PageRequest.of(0, BATCH_SIZE))
                : tasks.findDueReminderBatchAfter(ACTIVE_STATUSES, from, to,
                        cursor.dueAt(), cursor.taskId(), PageRequest.of(0, BATCH_SIZE));
        for (Task task : page) {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(task.getGroup().getTimezone()));
            if (task.getDueAt().isBefore(now) || task.getDueAt().isAfter(now.plusHours(24))) continue;
            long seconds = Duration.between(now, task.getDueAt()).getSeconds();
            notifications.taskDueSoon(task, Math.max(1, (seconds + 3599) / 3600));
        }
        if (page.isEmpty()) return null;
        Task last = page.get(page.size() - 1);
        return new Cursor(last.getDueAt(), last.getId(), page.size() == BATCH_SIZE);
    }

    public record Cursor(LocalDateTime dueAt, Long taskId, boolean hasMore) {}
}
