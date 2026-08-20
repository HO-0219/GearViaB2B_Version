package com.teamproject.authentication.domain.oauth;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_signup_requests")
public class OAuthSignupRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false, length = 20)
    private String provider;
    @Column(nullable = false, length = 255)
    private String providerSubject;
    @Column(nullable = false, length = 255)
    private String email;
    @Column(nullable = false, length = 60)
    private String name;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected OAuthSignupRequest() {}

    public OAuthSignupRequest(String tokenHash, String provider, String providerSubject,
            String email, String name, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.email = email;
        this.name = name;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(LocalDateTime now) { return expiresAt.isAfter(now); }
    public String getProvider() { return provider; }
    public String getProviderSubject() { return providerSubject; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
