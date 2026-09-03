package com.teamproject.mcp.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

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

    /** How often a still-identical usage is written back — every request would be write amplification. */
    private static final Duration TOUCH_INTERVAL = Duration.ofSeconds(60);

    public void used(String ip, String client) {
        String nextIp = limit(ip, 64);
        String nextClient = limit(client, 60);
        boolean sourceChanged = !Objects.equals(nextIp, lastIp) || !Objects.equals(nextClient, clientLabel);
        boolean stale = lastUsedAt == null || lastUsedAt.isBefore(LocalDateTime.now().minus(TOUCH_INTERVAL));
        if (!sourceChanged && !stale) {
            return;
        }
        this.lastUsedAt = LocalDateTime.now();
        this.lastIp = nextIp;
        this.clientLabel = nextClient;
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
