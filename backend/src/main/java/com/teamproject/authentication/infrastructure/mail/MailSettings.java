package com.teamproject.authentication.infrastructure.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "mail_settings")
public class MailSettings {
    public static final long SINGLETON_ID = 1L;

    @Id private Long id;
    @Column(nullable = false, length = 255) private String host;
    @Column(nullable = false) private int port;
    @Column(length = 255) private String username;
    @Column(name = "encrypted_password", length = 1000) private String encryptedPassword;
    @Column(name = "smtp_auth", nullable = false) private boolean smtpAuth;
    @Column(nullable = false) private boolean starttls;
    @Column(name = "from_address", nullable = false, length = 320) private String fromAddress;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected MailSettings() {}

    public MailSettings(String host, int port, String username, String encryptedPassword,
            boolean smtpAuth, boolean starttls, String fromAddress, boolean enabled) {
        this.id = SINGLETON_ID;
        update(host, port, username, encryptedPassword, smtpAuth, starttls, fromAddress, enabled);
    }

    public void update(String host, int port, String username, String encryptedPassword,
            boolean smtpAuth, boolean starttls, String fromAddress, boolean enabled) {
        this.host = host; this.port = port; this.username = username;
        this.encryptedPassword = encryptedPassword; this.smtpAuth = smtpAuth;
        this.starttls = starttls; this.fromAddress = fromAddress; this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public boolean isSmtpAuth() { return smtpAuth; }
    public boolean isStarttls() { return starttls; }
    public String getFromAddress() { return fromAddress; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
