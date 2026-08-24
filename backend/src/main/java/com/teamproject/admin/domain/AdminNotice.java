package com.teamproject.admin.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_notices")
public class AdminNotice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 2000)
    private String message;
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "recipient_count")
    private Integer recipientCount;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    protected AdminNotice() {}

    public AdminNotice(String title, String message, LocalDateTime scheduledAt, User createdBy) {
        this.title = title;
        this.message = message;
        this.scheduledAt = scheduledAt;
        this.status = Status.PENDING;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public void markSent(int recipientCount) {
        this.status = Status.SENT;
        this.recipientCount = recipientCount;
        this.sentAt = LocalDateTime.now();
    }

    public void cancel() { this.status = Status.CANCELLED; }
    public boolean isPending() { return status == Status.PENDING; }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public Status getStatus() { return status; }
    public Integer getRecipientCount() { return recipientCount; }
    public User getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }

    public enum Status { PENDING, SENT, CANCELLED }
}
