package com.teamproject.user;

import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class UserProfileApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signupService;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;

    @Test
    void authenticatedUserCanReadAndUpdateProfile() throws Exception {
        String email = "profile@example.com";
        String code = oneTimeTokens.issueCode(email);
        signupService.signup(new SignupRequest("profile_user", email, "프로필 사용자", "password123!", code));
        String accessToken = sessions.login(new LoginRequest("profile_user", "password123!")).response().accessToken();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("프로필 사용자"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.systemRole").value("USER"));

        mvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"새 닉네임","phoneNumber":"010-1234-5678","profileImageUrl":"https://example.com/profile.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새 닉네임"))
                .andExpect(jsonPath("$.phoneNumber").value("010-1234-5678"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/profile.png"));
    }

    @Test
    void profileRequiresAuthenticationAndValidInput() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        String email = "invalid-profile@example.com";
        String code = oneTimeTokens.issueCode(email);
        signupService.signup(new SignupRequest("invalid_profile", email, "검증 사용자", "password123!", code));
        String accessToken = sessions.login(new LoginRequest("invalid_profile", "password123!")).response().accessToken();

        mvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\" \",\"phoneNumber\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.nickname").exists())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());
    }

    @Test
    void inactiveAccountCannotUsePreviouslyIssuedAccessToken() throws Exception {
        String email = "suspended-profile@example.com";
        String code = oneTimeTokens.issueCode(email);
        signupService.signup(new SignupRequest("suspended_user", email, "정지 사용자", "password123!", code));
        String accessToken = sessions.login(new LoginRequest("suspended_user", "password123!")).response().accessToken();
        users.findByEmailIgnoreCase(email).orElseThrow().suspend();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
