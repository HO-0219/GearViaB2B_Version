package com.teamproject.assistant.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiAssistantActionRepository extends JpaRepository<AiAssistantAction, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AiAssistantAction a where a.id = :id and a.user.id = :userId")
    Optional<AiAssistantAction> findByIdAndUserIdForUpdate(@Param("id") Long id,
            @Param("userId") Long userId);
}
