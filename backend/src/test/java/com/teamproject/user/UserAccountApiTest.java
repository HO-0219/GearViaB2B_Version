package com.teamproject.user;

import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.IssuedTokens;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class UserAccountApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signupService;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;

    @Test
    void passwordChangeRequiresCurrentPasswordAndRevokesRefreshTokens() throws Exception {
        IssuedTokens tokens = signupAndLogin("password_user", "password@example.com");

        mvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + tokens.response().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"newPassword123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

        mvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + tokens.response().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123!\",\"newPassword\":\"newPassword123!\"}"))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> sessions.refresh(tokens.refreshToken())).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> sessions.login(new LoginRequest("password_user", "password123!")))
                .isInstanceOf(ApplicationException.class);
        assertThat(sessions.login(new LoginRequest("password_user", "newPassword123!")).response().accessToken()).isNotBlank();
    }

    @Test
    void withdrawalAnonymizesUserAndBlocksAllAuthentication() throws Exception {
        IssuedTokens tokens = signupAndLogin("withdraw_user", "withdraw@example.com");
        Long userId = users.findByUsernameIgnoreCase("withdraw_user").orElseThrow().getId();

        mvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.response().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123!\"}"))
                .andExpect(status().isNoContent());

        User withdrawn = users.findById(userId).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(User.Status.WITHDRAWN);
        assertThat(withdrawn.getUsername()).startsWith("withdrawn_");
        assertThat(withdrawn.getEmail()).endsWith("@invalid.local");
        assertThat(withdrawn.getName()).isEqualTo("탈퇴한 사용자");
        assertThat(withdrawn.getPhoneNumber()).isNull();
        assertThat(withdrawn.getProfileImageUrl()).isNull();
        assertThat(withdrawn.getPasswordHash()).isNull();
        assertThat(withdrawn.getWithdrawnAt()).isNotNull();
        assertThatThrownBy(() -> sessions.refresh(tokens.refreshToken())).isInstanceOf(ApplicationException.class);
    }

    private IssuedTokens signupAndLogin(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signupService.signup(new SignupRequest(username, email, "계정 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!"));
    }
}
