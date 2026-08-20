package com.teamproject.chat.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("select m from ChatMessage m where m.channel.id = :channelId and m.createdAt >= :cutoff " +
            "and (:beforeId is null or m.id < :beforeId) order by m.id desc")
    List<ChatMessage> history(@Param("channelId") Long channelId, @Param("cutoff") LocalDateTime cutoff,
            @Param("beforeId") Long beforeId, Pageable pageable);
    @Query("select coalesce(sum(m.sizeBytes), 0) from ChatMessage m where m.channel.group.id = :groupId and m.sizeBytes is not null")
    long sumAttachmentBytesByGroupId(@Param("groupId") Long groupId);
    List<ChatMessage> findByCreatedAtBeforeOrderByIdAsc(LocalDateTime cutoff, Pageable pageable);
}
