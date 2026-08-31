package com.teamproject.notification.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Slice<Notification> findByRecipientIdOrderByIdDesc(Long recipientId, Pageable pageable);
    Slice<Notification> findByRecipientIdAndIdLessThanOrderByIdDesc(Long recipientId, Long id, Pageable pageable);
    @Query("select n from Notification n where n.recipient.id = :recipientId "
            + "and (:cursor is null or n.id < :cursor) and (:groupId is null or n.group.id = :groupId) "
            + "and (:type is null or n.type = :type) and (:createdAfter is null or n.createdAt >= :createdAfter) "
            + "and (:read is null or (:read = true and n.readAt is not null) or (:read = false and n.readAt is null)) "
            + "order by n.id desc")
    Slice<Notification> findFiltered(Long recipientId, Long cursor, Long groupId, Notification.Type type,
            LocalDateTime createdAfter, Boolean read, Pageable pageable);
    Slice<Notification> findByRecipientIdAndReadAtIsNullOrderByIdDesc(Long recipientId, Pageable pageable);
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);
    long countByRecipientIdAndReadAtIsNull(Long recipientId);
    Optional<Notification> findByRecipientIdAndEventKey(Long recipientId, String eventKey);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO notifications
                (recipient_user_id, actor_user_id, group_id, task_id, comment_id,
                 type, event_key, title, message, read_at, created_at)
            VALUES
                (:recipientId, :actorId, :groupId, :taskId, :commentId,
                 :type, :eventKey, :title, :message, NULL, :createdAt)
            """, nativeQuery = true)
    int insertIgnore(@Param("recipientId") Long recipientId,
            @Param("actorId") Long actorId,
            @Param("groupId") Long groupId,
            @Param("taskId") Long taskId,
            @Param("commentId") Long commentId,
            @Param("type") String type,
            @Param("eventKey") String eventKey,
            @Param("title") String title,
            @Param("message") String message,
            @Param("createdAt") LocalDateTime createdAt);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :readAt where n.recipient.id = :recipientId and n.readAt is null")
    int markAllRead(Long recipientId, LocalDateTime readAt);
}
