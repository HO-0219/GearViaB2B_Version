package com.teamproject.user.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_consents")
public class UserConsent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private Type consentType;
    @Column(nullable = false, length = 30)
    private String policyVersion;
    @Column(nullable = false)
    private boolean agreed;
    @Column(nullable = false)
    private LocalDateTime agreedAt;
    @Column(nullable = false, length = 30)
    private String source;

    protected UserConsent() {}

    public UserConsent(User user, Type consentType, String policyVersion, boolean agreed, String source) {
        this.user = user;
        this.consentType = consentType;
        this.policyVersion = policyVersion;
        this.agreed = agreed;
        this.source = source;
        this.agreedAt = LocalDateTime.now();
    }

    public boolean isAgreed() { return agreed; }

    public enum Type { TERMS, PRIVACY_COLLECTION, AGE_14_OR_OLDER, SERVICE_NOTIFICATIONS, MARKETING_MESSAGES }
}
