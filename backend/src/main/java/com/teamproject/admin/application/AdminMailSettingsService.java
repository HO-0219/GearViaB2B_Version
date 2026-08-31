package com.teamproject.admin.application;

import com.teamproject.admin.security.AdminMfaCipher;
import com.teamproject.authentication.infrastructure.mail.MailSenderFactory;
import com.teamproject.authentication.infrastructure.mail.MailSettings;
import com.teamproject.authentication.infrastructure.mail.MailSettingsRepository;
import com.teamproject.common.exception.ApplicationException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AdminMailSettingsService {
    private final MailSettingsRepository repository;
    private final MailSenderFactory factory;
    private final AdminMfaCipher cipher;

    public AdminMailSettingsService(MailSettingsRepository repository, MailSenderFactory factory, AdminMfaCipher cipher) {
        this.repository = repository; this.factory = factory; this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public StatusResponse status() { return response(factory.effectiveSettings()); }

    @Transactional
    public StatusResponse update(UpdateRequest request) {
        validate(request.host(), request.port(), request.fromAddress());
        MailSettings existing = repository.findById(MailSettings.SINGLETON_ID).orElse(null);
        String encrypted = existing == null ? null : existing.getEncryptedPassword();
        if (request.password() != null) {
            encrypted = request.password().isBlank() ? null : encrypt(request.password());
        }
        String username = blankToNull(request.username());
        if (request.enabled() && request.smtpAuth() && (username == null || encrypted == null))
            throw invalid("SMTP 인증을 활성화하려면 사용자명과 비밀번호가 필요합니다.");
        if (existing == null) existing = new MailSettings(request.host().trim(), request.port(), username, encrypted,
                request.smtpAuth(), request.starttls(), request.fromAddress().trim(), request.enabled());
        else existing.update(request.host().trim(), request.port(), username, encrypted,
                request.smtpAuth(), request.starttls(), request.fromAddress().trim(), request.enabled());
        repository.save(existing);
        return status();
    }

    public TestResponse test(TestRequest request) {
        validate(request.host(), request.port(), request.fromAddress());
        String password = request.password();
        if (password == null) password = factory.effectiveSettings().password();
        if (request.smtpAuth() && (blankToNull(request.username()) == null || blankToNull(password) == null))
            throw invalid("SMTP 인증 테스트에는 사용자명과 비밀번호가 필요합니다.");
        var candidate = new MailSenderFactory.EffectiveSettings(request.host().trim(), request.port(),
                blankToNull(request.username()), password, request.smtpAuth(), request.starttls(),
                request.fromAddress().trim(), true, MailSenderFactory.Source.DATABASE,
                password != null && !password.isBlank(), null);
        try { factory.createSender(candidate).sender().testConnection(); return new TestResponse(true, "SMTP 연결에 성공했습니다."); }
        catch (Exception exception) { return new TestResponse(false, "SMTP 연결에 실패했습니다: " + exception.getClass().getSimpleName()); }
    }

    private String encrypt(String password) {
        if (!cipher.configured()) throw new ApplicationException("MAIL_ENCRYPTION_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                "관리자 암호화 키가 설정되지 않아 SMTP 비밀번호를 저장할 수 없습니다.");
        return cipher.encrypt(password);
    }
    private void validate(String host, int port, String from) {
        if (host == null || host.isBlank() || host.length() > 255) throw invalid("SMTP 호스트를 확인해 주세요.");
        if (port < 1 || port > 65535) throw invalid("SMTP 포트는 1~65535 범위여야 합니다.");
        try { InternetAddress address = new InternetAddress(from); address.validate(); }
        catch (Exception exception) { throw invalid("발신 이메일 주소를 확인해 주세요."); }
    }
    private ApplicationException invalid(String message) { return new ApplicationException("INVALID_MAIL_SETTINGS", HttpStatus.BAD_REQUEST, message); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private StatusResponse response(MailSenderFactory.EffectiveSettings v) {
        return new StatusResponse(v.host(), v.port(), v.username(), v.passwordConfigured(), v.smtpAuth(), v.starttls(),
                v.fromAddress(), v.enabled(), v.source(), cipher.configured(), v.updatedAt());
    }
    public record UpdateRequest(String host, int port, String username, String password, boolean smtpAuth, boolean starttls, String fromAddress, boolean enabled) {}
    public record TestRequest(String host, int port, String username, String password, boolean smtpAuth, boolean starttls, String fromAddress) {}
    public record TestResponse(boolean success, String message) {}
    public record StatusResponse(String host, int port, String username, boolean passwordConfigured, boolean smtpAuth,
            boolean starttls, String fromAddress, boolean enabled, MailSenderFactory.Source source,
            boolean encryptionConfigured, LocalDateTime updatedAt) {}
}
