package com.teamproject.authentication.infrastructure.mail;

import com.teamproject.admin.security.AdminMfaCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Properties;

@Component
public class MailSenderFactory {
    private static final Logger log = LoggerFactory.getLogger(MailSenderFactory.class);
    private final MailSettingsRepository settings;
    private final AdminMfaCipher cipher;
    private final EffectiveSettings defaults;

    public MailSenderFactory(MailSettingsRepository settings, AdminMfaCipher cipher,
            @Value("${spring.mail.host:localhost}") String host,
            @Value("${spring.mail.port:1025}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean smtpAuth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean starttls,
            @Value("${app.mail.from:noreply@localhost}") String fromAddress,
            @Value("${app.mail.enabled:false}") boolean enabled) {
        this.settings = settings; this.cipher = cipher;
        this.defaults = new EffectiveSettings(host, port, username, password, smtpAuth, starttls,
                fromAddress, enabled, Source.ENVIRONMENT, password != null && !password.isBlank(), null);
    }

    public ConfiguredSender createSender() {
        return createSender(effectiveSettings());
    }

    public ConfiguredSender createSender(EffectiveSettings value) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(value.host()); sender.setPort(value.port());
        sender.setUsername(value.username()); sender.setPassword(value.password());
        Properties props = sender.getJavaMailProperties();
        props.setProperty("mail.smtp.auth", Boolean.toString(value.smtpAuth()));
        props.setProperty("mail.smtp.starttls.enable", Boolean.toString(value.starttls()));
        props.setProperty("mail.smtp.connectiontimeout", "5000");
        props.setProperty("mail.smtp.timeout", "5000");
        props.setProperty("mail.smtp.writetimeout", "5000");
        return new ConfiguredSender(sender, value.fromAddress(), value.enabled(), value.source());
    }

    public EffectiveSettings effectiveSettings() {
        return settings.findById(MailSettings.SINGLETON_ID).map(value -> {
            String password = "";
            boolean passwordConfigured = value.getEncryptedPassword() != null && !value.getEncryptedPassword().isBlank();
            boolean enabled = value.isEnabled();
            if (passwordConfigured) try { password = cipher.decrypt(value.getEncryptedPassword()); }
            catch (RuntimeException exception) {
                log.warn("Stored SMTP password could not be decrypted ({}); disabling mail until it is replaced.",
                        exception.getClass().getSimpleName());
                enabled = false; passwordConfigured = false;
            }
            return new EffectiveSettings(value.getHost(), value.getPort(), value.getUsername(), password,
                    value.isSmtpAuth(), value.isStarttls(), value.getFromAddress(), enabled,
                    Source.DATABASE, passwordConfigured, value.getUpdatedAt());
        }).orElse(defaults);
    }

    public enum Source { DATABASE, ENVIRONMENT }
    public record ConfiguredSender(JavaMailSenderImpl sender, String fromAddress, boolean enabled, Source source) {}
    public record EffectiveSettings(String host, int port, String username, String password,
            boolean smtpAuth, boolean starttls, String fromAddress, boolean enabled, Source source,
            boolean passwordConfigured, LocalDateTime updatedAt) {}
}
