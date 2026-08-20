package com.teamproject.task.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDateTime;

public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, Long> {
    List<TaskStatusHistory> findAllByTaskIdOrderByCreatedAtAsc(Long taskId);
    boolean existsByTaskIdAndFromStatusAndToStatusAndCreatedAtAfter(
            Long taskId, Task.Status fromStatus, Task.Status toStatus, LocalDateTime createdAt);
}
