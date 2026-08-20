package com.teamproject.assistant.domain;

import com.teamproject.group.domain.Group;
import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_assistant_messages")
public class AiAssistantMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_id")
    private AiAssistantAction action;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AiAssistantMessage() {}

    public AiAssistantMessage(User user, Group group, Role role, String content,
            AiAssistantAction action) {
        this.user = user;
        this.group = group;
        this.role = role;
        this.content = content;
        this.action = action;
    }

    public Long getId() { return id; }
    public AiAssistantAction getAction() { return action; }
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public enum Role { USER, ASSISTANT }
}
