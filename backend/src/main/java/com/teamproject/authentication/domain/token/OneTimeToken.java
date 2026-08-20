package com.teamproject.authentication.domain.token;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "one_time_tokens")
public class OneTimeToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Purpose purpose;
    @Column(nullable = false, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected OneTimeToken() {}
    public OneTimeToken(String email, Purpose purpose, String tokenHash, LocalDateTime expiresAt) {
        this.email = email;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
    public boolean isUsable(String hash, LocalDateTime now) {
        return usedAt == null && expiresAt.isAfter(now) && tokenHash.equals(hash);
    }
    public void use(LocalDateTime now) { this.usedAt = now; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public enum Purpose { SIGNUP, PASSWORD_RESET }
}
