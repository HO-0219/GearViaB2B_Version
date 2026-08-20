package com.teamproject.authentication;

import com.teamproject.authentication.application.dto.RecoveryDtos.PasswordResetConfirmRequest;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SessionDtos.MeResponse;
import com.teamproject.authentication.application.dto.SessionDtos.TokenResponse;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDtoLoggingTest {
    @Test
    void sensitiveRequestValuesAreNotExposedByToString() {
        var signup = new SignupRequest(
                "private-user", "private@example.com", "Private Name",
                "private-password", "123456");
        var login = new LoginRequest("private-user", "private-password");
        var reset = new PasswordResetConfirmRequest(
                "private@example.com", "private-reset-token", "new-private-password");

        assertThat(signup.toString())
                .doesNotContain("private-user", "private@example.com", "Private Name", "private-password", "123456");
        assertThat(login.toString()).doesNotContain("private-user", "private-password");
        assertThat(reset.toString())
                .doesNotContain("private@example.com", "private-reset-token", "new-private-password");
    }

    @Test
    void tokenAndProfileResponsesDoNotExposeTheirPayloadByToString() {
        var token = new TokenResponse("private-access-token", "Bearer", 3600);
        var signup = new SignupResponse(1L, "private-user", "private@example.com", "Private Name");
        var me = new MeResponse(1L, "private-user", "private@example.com", "Private Name", "USER");

        assertThat(token.toString()).doesNotContain("private-access-token");
        assertThat(signup.toString()).doesNotContain("private-user", "private@example.com", "Private Name");
        assertThat(me.toString()).doesNotContain("private-user", "private@example.com", "Private Name");
    }
}
