package com.teamproject.chat.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ChatSocketTicketRepository extends JpaRepository<ChatSocketTicket, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ChatSocketTicket t where t.tokenHash = :hash " +
            "and t.consumedAt is null and t.expiresAt > :now")
    Optional<ChatSocketTicket> findConsumableForUpdate(
            @Param("hash") String hash, @Param("now") LocalDateTime now);
    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
