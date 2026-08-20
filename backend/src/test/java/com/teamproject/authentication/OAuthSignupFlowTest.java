package com.teamproject.authentication;

import com.teamproject.authentication.application.OAuthLoginService;
import com.teamproject.authentication.application.dto.OAuthDtos.SignupCompleteRequest;
import com.teamproject.authentication.domain.oauth.SocialAccountRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.user.domain.UserConsentRepository;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class OAuthSignupFlowTest {
    @Autowired OAuthLoginService oauth;
    @Autowired UserRepository users;
    @Autowired SocialAccountRepository socialAccounts;
    @Autowired UserConsentRepository consents;

    @Test
    void createsNoAccountUntilRequiredConsentIsCompleted() {
        var start = oauth.start("google", "google-subject-1", "Google.Member@Example.com", "Google Member", true);

        assertThat(start.requiresConsent()).isTrue();
        assertThat(users.existsByEmailIgnoreCase("google.member@example.com")).isFalse();
        assertThat(oauth.status(start.signupToken()).email()).isEqualTo("google.member@example.com");

        var tokens = oauth.complete(start.signupToken(),
                new SignupCompleteRequest(true, true, true, false, true));

        assertThat(tokens.response().accessToken()).isNotBlank();
        var user = users.findByEmailIgnoreCase("google.member@example.com").orElseThrow();
        assertThat(socialAccounts.findByProviderAndProviderSubject("google", "google-subject-1")).isPresent();
        assertThat(consents.findAllByUserId(user.getId())).hasSize(5)
                .anySatisfy(consent -> assertThat(consent).isNotNull());
        assertThatThrownBy(() -> oauth.status(start.signupToken()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("만료");
    }

    @Test
    void rejectsUnverifiedGoogleEmail() {
        assertThatThrownBy(() -> oauth.start(
                "google", "google-subject-2", "unverified@example.com", "Unverified", false))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("확인된 이메일");
        assertThat(users.existsByEmailIgnoreCase("unverified@example.com")).isFalse();
    }
}
