package com.teamproject.authentication.domain.oauth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderSubject(String provider, String providerSubject);
    void deleteAllByUserId(Long userId);
}
