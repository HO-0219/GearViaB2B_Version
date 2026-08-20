package com.teamproject.authentication.domain.token;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, Long> {
    Optional<OneTimeToken> findFirstByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(String email, OneTimeToken.Purpose purpose);
    void deleteAllByEmailIgnoreCase(String email);
}
