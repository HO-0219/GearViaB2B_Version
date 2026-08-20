package com.teamproject.notification.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 2048)
    private String endpoint;
    @Column(name = "p256dh_key", nullable = false, length = 255)
    private String p256dhKey;
    @Column(name = "auth_secret", nullable = false, length = 255)
    private String authSecret;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected PushSubscription() {}

    public PushSubscription(User user, String endpoint, String p256dhKey, String authSecret) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dhKey = p256dhKey;
        this.authSecret = authSecret;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void refresh(User user, String p256dhKey, String authSecret) {
        this.user = user;
        this.p256dhKey = p256dhKey;
        this.authSecret = authSecret;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getEndpoint() { return endpoint; }
    public String getP256dhKey() { return p256dhKey; }
    public String getAuthSecret() { return authSecret; }
}
