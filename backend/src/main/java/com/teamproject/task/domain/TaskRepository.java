package com.teamproject.task.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByGroupIdOrderByCreatedAtDesc(Long groupId);
    Page<Task> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
    /** Bypasses {@code @SQLRestriction("deleted_at IS NULL")} so admins can look up a soft-deleted task to restore it. */
    @Query(value = "select * from tasks where id = :id", nativeQuery = true)
    Optional<Task> findByIdIncludingDeleted(@Param("id") Long id);
    @Query(value = "select * from tasks where deleted_at is not null order by deleted_at desc limit 50", nativeQuery = true)
    List<Task> findRecentlyDeleted();
    List<Task> findByGroupIdOrderByUpdatedAtDescIdDesc(Long groupId, Pageable pageable);
    List<Task> findAllByGroupIdAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAscIdAsc(
            Long groupId, LocalDateTime from, LocalDateTime to);
    @Query("select t from Task t where t.group.id = :groupId and t.dueAt is not null "
            + "and t.dueAt >= :from and t.createdAt < :to order by t.dueAt, t.id")
    List<Task> findAllTimelineTasks(Long groupId, LocalDateTime from, LocalDateTime to);
    List<Task> findAllByAssigneeUserIdAndAssigneeStatus(Long userId,
            com.teamproject.group.domain.GroupMember.Status status);
    @Query("select t from Task t where t.dueAt >= :from and t.dueAt < :to "
            + "and t.status in :statuses order by t.dueAt, t.id")
    List<Task> findDueReminderBatch(Collection<Task.Status> statuses,
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("select t from Task t where t.dueAt >= :from and t.dueAt < :to "
            + "and t.status in :statuses and (t.dueAt > :cursorDueAt "
            + "or (t.dueAt = :cursorDueAt and t.id > :cursorId)) order by t.dueAt, t.id")
    List<Task> findDueReminderBatchAfter(Collection<Task.Status> statuses,
            LocalDateTime from, LocalDateTime to, LocalDateTime cursorDueAt,
            Long cursorId, Pageable pageable);
}
