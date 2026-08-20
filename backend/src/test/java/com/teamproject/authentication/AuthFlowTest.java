package com.teamproject.authentication;

import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.IssuedTokens;
import com.teamproject.authentication.application.RecoveryService;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.RecoveryDtos.PasswordResetConfirmRequest;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupResponse;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.user.domain.UserRepository;
import com.teamproject.common.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TeamProjectApplication.class)
@Transactional
class AuthFlowTest {
    @Autowired SignupService signupService;
    @Autowired SessionService sessions;
    @Autowired RecoveryService recovery;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;

    @Test
    void signupLoginRefreshAndPasswordReset() {
        String email = "member@example.com";
        String code = oneTimeTokens.issueCode(email);

        SignupResponse signup = signupService.signup(new SignupRequest("team_member", email, "팀원", "password123!", code));
        assertThat(signup.username()).isEqualTo("team_member");
        assertThat(users.existsByEmailIgnoreCase(email)).isTrue();

        IssuedTokens login = sessions.login(new LoginRequest("team_member", "password123!"));
        assertThat(login.response().accessToken()).isNotBlank();
        assertThat(sessions.refresh(login.refreshToken()).response().accessToken()).isNotBlank();

        String resetToken = oneTimeTokens.issueResetToken(email);
        recovery.resetPassword(new PasswordResetConfirmRequest(email, resetToken, "changedPassword123!"));
        assertThat(sessions.login(new LoginRequest("team_member", "changedPassword123!")).response().accessToken()).isNotBlank();
    }

    @Test
    void inactiveAccountCannotLoginOrRefresh() {
        String email = "inactive@example.com";
        String code = oneTimeTokens.issueCode(email);
        signupService.signup(new SignupRequest("inactive_user", email, "비활성 사용자", "password123!", code));
        IssuedTokens tokens = sessions.login(new LoginRequest("inactive_user", "password123!"));

        users.findByEmailIgnoreCase(email).orElseThrow().suspend();

        assertThatThrownBy(() -> sessions.login(new LoginRequest("inactive_user", "password123!")))
                .isInstanceOf(ApplicationException.class)
                .extracting(error -> ((ApplicationException) error).code())
                .isEqualTo("ACCOUNT_INACTIVE");
        assertThatThrownBy(() -> sessions.refresh(tokens.refreshToken()))
                .isInstanceOf(ApplicationException.class)
                .extracting(error -> ((ApplicationException) error).code())
                .isEqualTo("ACCOUNT_INACTIVE");
    }
}
