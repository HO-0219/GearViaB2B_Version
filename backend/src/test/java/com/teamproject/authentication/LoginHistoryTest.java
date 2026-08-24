package com.teamproject.authentication;

import com.teamproject.B2BGearViaApplication;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.authentication.domain.LoginHistory;
import com.teamproject.authentication.domain.LoginHistoryRepository;
import com.teamproject.common.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No class-level @Transactional here on purpose: login() runs as its own top-level transaction
 * (like a real request would), which is exactly what proves the noRollbackFor annotation on
 * SessionService.login keeps a failed attempt's history row even though login() then throws.
 */
@SpringBootTest(classes = B2BGearViaApplication.class)
class LoginHistoryTest {
    @Autowired SessionService sessions;
    @Autowired SignupService signupService;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired LoginHistoryRepository history;

    @Test
    void recordsAFailedLoginAttemptDespiteTheThrownException() {
        String identifier = "no-such-user-" + System.nanoTime();

        assertThatThrownBy(() -> sessions.login(new LoginRequest(identifier, "wrong-password")))
                .isInstanceOf(ApplicationException.class);

        var recent = history.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, 5)).getContent();
        assertThat(recent).anySatisfy(entry -> {
            assertThat(entry.getUsername()).isEqualTo(identifier);
            assertThat(entry.getOutcome()).isEqualTo(LoginHistory.Outcome.FAILURE);
            assertThat(entry.getUser()).isNull();
        });
    }

    @Test
    void recordsASuccessfulLoginLinkedToTheUser() {
        String email = "login-history-" + System.nanoTime() + "@example.com";
        String code = oneTimeTokens.issueCode(email);
        var signup = signupService.signup(new SignupRequest("history_user_" + System.nanoTime(), email, "이력테스트", "password123!", code));

        sessions.login(new LoginRequest(signup.username(), "password123!"));

        var recent = history.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, 5)).getContent();
        assertThat(recent).anySatisfy(entry -> {
            assertThat(entry.getUsername()).isEqualTo(signup.username());
            assertThat(entry.getOutcome()).isEqualTo(LoginHistory.Outcome.SUCCESS);
            assertThat(entry.getUser()).isNotNull();
        });
    }
}
