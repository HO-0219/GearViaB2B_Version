package com.teamproject.admin.application;

import com.teamproject.admin.domain.*;
import com.teamproject.admin.security.*;
import com.teamproject.authentication.domain.token.RefreshToken;
import com.teamproject.authentication.domain.token.RefreshTokenRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.user.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Service
public class AdminMfaService {
    private final AdminMfaCredentialRepository credentials;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AdminMfaCipher cipher;
    private final TotpService totp;
    public AdminMfaService(AdminMfaCredentialRepository credentials, UserRepository users,
            RefreshTokenRepository refreshTokens, AdminMfaCipher cipher, TotpService totp) {
        this.credentials = credentials; this.users = users; this.refreshTokens = refreshTokens;
        this.cipher = cipher; this.totp = totp;
    }
    @Transactional(readOnly = true)
    public Status status(Long userId, boolean sessionVerified) {
        User user = admin(userId);
        var value = credentials.findByUserId(userId);
        return new Status(value.filter(AdminMfaCredential::isEnabled).isPresent(), sessionVerified,
                cipher.configured(), value.map(AdminMfaCredential::getEnabledAt).orElse(null));
    }
    @Transactional
    public Setup setup(Long userId) {
        User user = admin(userId);
        if (!cipher.configured()) throw unavailable();
        String secret = totp.secret();
        AdminMfaCredential value = credentials.findByUserId(userId).orElse(null);
        if (value != null && value.isEnabled()) {
            throw new ApplicationException("ADMIN_MFA_ALREADY_ENABLED", HttpStatus.CONFLICT, "관리자 MFA가 이미 활성화되어 있습니다.");
        }
        if (value == null) credentials.save(new AdminMfaCredential(user, cipher.encrypt(secret)));
        else value.replacePendingSecret(cipher.encrypt(secret));
        String issuer = "B2BGearVia";
        String label = issuer + ":" + user.getUsername();
        String uri = "otpauth://totp/" + url(label) + "?secret=" + secret + "&issuer=" + url(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
        return new Setup(secret, uri);
    }
    @Transactional
    public void confirm(Long userId, String code) {
        User user = admin(userId);
        AdminMfaCredential value = credentials.findByUserId(userId).orElseThrow(() ->
                new ApplicationException("ADMIN_MFA_SETUP_REQUIRED", HttpStatus.CONFLICT, "관리자 MFA 설정을 먼저 시작해 주세요."));
        if (value.isEnabled()) throw new ApplicationException("ADMIN_MFA_ALREADY_ENABLED", HttpStatus.CONFLICT, "관리자 MFA가 이미 활성화되어 있습니다.");
        if (!totp.verify(cipher.decrypt(value.getEncryptedSecret()), code)) throw invalid();
        value.enable(null);
        refreshTokens.findAllByUserId(userId).forEach(RefreshToken::revoke);
        user.invalidateSessions();
    }
    @Transactional(noRollbackFor = ApplicationException.class)
    public boolean verifyForLogin(User user, String code) {
        if (user.getSystemRole() != User.SystemRole.ADMIN) return false;
        AdminMfaCredential value = credentials.findByUserId(user.getId()).orElse(null);
        if (value == null || !value.isEnabled()) return false;
        if (totp.verify(cipher.decrypt(value.getEncryptedSecret()), code)) return true;
        throw new ApplicationException("ADMIN_MFA_REQUIRED", HttpStatus.UNAUTHORIZED,
                "관리자 인증 앱 코드 6자리를 입력해 주세요.");
    }
    private User admin(Long id) {
        return users.findById(id).filter(value -> value.getSystemRole() == User.SystemRole.ADMIN)
                .orElseThrow(() -> new ApplicationException("ADMIN_REQUIRED", HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."));
    }
    private String url(String value) { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private ApplicationException invalid() {
        return new ApplicationException("ADMIN_MFA_CODE_INVALID", HttpStatus.UNAUTHORIZED, "관리자 MFA 코드가 올바르지 않습니다.");
    }
    private ApplicationException unavailable() {
        return new ApplicationException("ADMIN_MFA_ENCRYPTION_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                "관리자 MFA 암호화 키가 설정되지 않았습니다.");
    }
    public record Status(boolean enabled, boolean sessionVerified, boolean encryptionConfigured,
            java.time.LocalDateTime enabledAt) {}
    public record Setup(String secret, String otpauthUri) {}
}
