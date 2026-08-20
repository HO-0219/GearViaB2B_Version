package com.teamproject.chat.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_socket_tickets", indexes = @Index(
        name = "idx_chat_socket_tickets_expiry", columnList = "expires_at,consumed_at"))
public class ChatSocketTicket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false, length = 64, unique = true, columnDefinition = "char(64)") private String tokenHash;
    @Column(nullable = false) private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    protected ChatSocketTicket() {}
    public ChatSocketTicket(User user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user; this.tokenHash = tokenHash; this.expiresAt = expiresAt; this.createdAt = LocalDateTime.now();
    }
    public void consume(LocalDateTime now) { this.consumedAt = now; }
    public User getUser() { return user; }
}
