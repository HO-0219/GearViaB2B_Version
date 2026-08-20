package com.teamproject.authentication.application;

import com.teamproject.authentication.domain.oauth.OAuthSignupRequestRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component
public class OAuthSignupCleanup {
    private final OAuthSignupRequestRepository signupRequests;

    public OAuthSignupCleanup(OAuthSignupRequestRepository signupRequests) {
        this.signupRequests = signupRequests;
    }

    @Scheduled(fixedDelayString = "${app.oauth.signup-cleanup-ms:900000}")
    @Transactional
    public void deleteExpiredRequests() {
        signupRequests.deleteAllByExpiresAtBefore(LocalDateTime.now());
    }
}
