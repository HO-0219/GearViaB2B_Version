package com.teamproject.admin.application;

import com.teamproject.admin.domain.AdminMfaCredential;
import com.teamproject.admin.domain.AdminMfaCredentialRepository;
import com.teamproject.admin.security.AdminMfaCipher;
import com.teamproject.admin.security.TotpService;
import com.teamproject.authentication.domain.token.RefreshTokenRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminMfaServiceTotpOnlyTest {

    @Test
    void confirmationEnablesMfaWithoutCreatingRecoveryCodes() {
        Fixture fixture = new Fixture();
        when(fixture.totp.verify("plain-secret", "123456")).thenReturn(true);

        fixture.service.confirm(7L, "123456");

        assertThat(fixture.credential.isEnabled()).isTrue();
        assertThat(fixture.credential.getRecoveryCodeHashes()).isNull();
    }

    @Test
    void legacyRecoveryCodeCannotReplaceTheAuthenticatorCode() {
        Fixture fixture = new Fixture();
        fixture.credential.enable("1635c8525afbae58c37bede3c9440844e9143727cc7c160bed665ec378d8a262");
        when(fixture.totp.verify("plain-secret", "ABCD-1234")).thenReturn(false);

        assertThatThrownBy(() -> fixture.service.verifyForLogin(fixture.admin, "ABCD-1234"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("인증 앱 코드");
    }

    private static final class Fixture {
        private final AdminMfaCredentialRepository credentials = mock(AdminMfaCredentialRepository.class);
        private final UserRepository users = mock(UserRepository.class);
        private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
        private final AdminMfaCipher cipher = mock(AdminMfaCipher.class);
        private final TotpService totp = mock(TotpService.class);
        private final User admin = mock(User.class);
        private final AdminMfaCredential credential = new AdminMfaCredential(admin, "encrypted-secret");
        private final AdminMfaService service = new AdminMfaService(credentials, users, refreshTokens, cipher, totp);

        private Fixture() {
            when(admin.getId()).thenReturn(7L);
            when(admin.getSystemRole()).thenReturn(User.SystemRole.ADMIN);
            when(users.findById(7L)).thenReturn(Optional.of(admin));
            when(credentials.findByUserId(7L)).thenReturn(Optional.of(credential));
            when(cipher.decrypt("encrypted-secret")).thenReturn("plain-secret");
            when(refreshTokens.findAllByUserId(7L)).thenReturn(List.of());
        }
    }
}
