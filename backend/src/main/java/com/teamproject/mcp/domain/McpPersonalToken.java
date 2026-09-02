package com.teamproject.mcp.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mcp_personal_tokens", uniqueConstraints =
        @UniqueConstraint(name = "uk_mcp_personal_token_hash", columnNames = "token_hash"))
public class McpPersonalToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false, length = 60)
    private String label;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(nullable = false, length = 30)
    private String scope;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    @Column(name = "last_ip", length = 64)
    private String lastIp;
    @Column(name = "client_label", length = 60)
    private String clientLabel;
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected McpPersonalToken() {}

    public McpPersonalToken(User user, String label, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.label = label;
        this.tokenHash = tokenHash;
        this.scope = "READ";
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    public boolean usableAt(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now) && user.isActive();
    }

    public void used(String ip, String client) {
        this.lastUsedAt = LocalDateTime.now();
        this.lastIp = limit(ip, 64);
        this.clientLabel = limit(client, 60);
    }

    public void revoke() {
        if (revokedAt == null) revokedAt = LocalDateTime.now();
    }

    private String limit(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(max, trimmed.length()));
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getLabel() { return label; }
    public String getTokenHash() { return tokenHash; }
    public String getScope() { return scope; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public String getLastIp() { return lastIp; }
    public String getClientLabel() { return clientLabel; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
}
