package com.teamproject.authentication.domain.oauth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

public interface OAuthSignupRequestRepository extends JpaRepository<OAuthSignupRequest, Long> {
    Optional<OAuthSignupRequest> findByTokenHash(String tokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from OAuthSignupRequest request where request.tokenHash = :tokenHash")
    Optional<OAuthSignupRequest> findLockedByTokenHash(String tokenHash);
    long deleteAllByExpiresAtBefore(LocalDateTime cutoff);
}
