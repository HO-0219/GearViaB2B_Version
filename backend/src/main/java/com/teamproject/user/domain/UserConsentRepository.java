package com.teamproject.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {
    List<UserConsent> findAllByUserId(Long userId);
    Optional<UserConsent> findFirstByUserIdAndConsentTypeOrderByAgreedAtDescIdDesc(
            Long userId, UserConsent.Type consentType);
}
