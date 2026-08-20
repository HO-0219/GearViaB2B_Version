package com.teamproject.authentication.domain.oauth;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "social_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_social_provider_subject", columnNames = {"provider", "provider_subject"})
})
public class SocialAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;
    @Column(nullable = false, length = 20)
    private String provider;
    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    protected SocialAccount() {}
    public SocialAccount(User user, String provider, String providerSubject) {
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
    }
    public User getUser() { return user; }
}
