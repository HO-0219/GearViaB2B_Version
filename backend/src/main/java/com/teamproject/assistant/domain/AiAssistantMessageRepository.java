package com.teamproject.assistant.domain;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAssistantMessageRepository extends JpaRepository<AiAssistantMessage, Long> {
    @EntityGraph(attributePaths = "action")
    List<AiAssistantMessage> findByUserIdAndGroupIdOrderByIdDesc(
            Long userId, Long groupId, Pageable pageable);
    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
