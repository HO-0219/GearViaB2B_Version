package com.teamproject.assistant.domain;

import com.teamproject.group.domain.Group;
import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_assistant_actions")
public class AiAssistantAction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;
    @Column(nullable = false, length = 40)
    private String toolName;
    @Column(nullable = false, columnDefinition = "JSON")
    private String argumentsJson;
    @Column(nullable = false, length = 500)
    private String summary;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    private LocalDateTime executedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Version
    private long version;

    protected AiAssistantAction() {}

    public AiAssistantAction(User user, Group group, String toolName, String argumentsJson,
            String summary, LocalDateTime expiresAt) {
        this.user = user;
        this.group = group;
        this.toolName = toolName;
        this.argumentsJson = argumentsJson;
        this.summary = summary;
        this.expiresAt = expiresAt;
    }

    public void complete(LocalDateTime now) {
        status = Status.COMPLETED;
        executedAt = now;
    }
    public void cancel() { if (status == Status.PENDING) status = Status.CANCELLED; }
    public void expire() { if (status == Status.PENDING) status = Status.EXPIRED; }
    public boolean isExpiredAt(LocalDateTime now) { return !expiresAt.isAfter(now); }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public Group getGroup() { return group; }
    public String getToolName() { return toolName; }
    public String getArgumentsJson() { return argumentsJson; }
    public String getSummary() { return summary; }
    public Status getStatus() { return status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public enum Status { PENDING, COMPLETED, CANCELLED, EXPIRED }
}
