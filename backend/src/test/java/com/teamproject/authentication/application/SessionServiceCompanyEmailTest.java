package com.teamproject.authentication.application;

import com.teamproject.admin.application.AdminMfaService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SessionDtos.TokenResponse;
import com.teamproject.authentication.application.token.RefreshTokenService;
import com.teamproject.authentication.domain.token.RefreshToken.ClientMode;
import com.teamproject.authentication.domain.token.SessionDevice;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.notification.application.PushSubscriptionService;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceCompanyEmailTest {

    @Test
    void logsInAnEmployeeWithCompanyEmail() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        AccessSessionIssuer issuer = mock(AccessSessionIssuer.class);
        NotificationService notifications = mock(NotificationService.class);
        AdminMfaService adminMfa = mock(AdminMfaService.class);
        PushSubscriptionService pushSubscriptions = mock(PushSubscriptionService.class);
        SessionService service = new SessionService(users, passwordEncoder, refreshTokens, issuer,
                notifications, adminMfa, pushSubscriptions, false, "demo_leader");

        User employee = new User("employee_internal", "employee@company.com", "encoded", "홍길동", true);
        IssuedTokens expected = new IssuedTokens(new TokenResponse("access", "Bearer", 300), "refresh", 3600);
        when(users.findByEmailIgnoreCase("employee@company.com")).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches("user123", "encoded")).thenReturn(true);
        when(issuer.issue(eq(employee), eq(ClientMode.WEB), any(SessionDevice.class), eq(false)))
                .thenReturn(expected);

        IssuedTokens actual = service.login(new LoginRequest("employee@company.com", "user123"));

        assertThat(actual).isSameAs(expected);
        verify(users).findByEmailIgnoreCase("employee@company.com");
    }
}
