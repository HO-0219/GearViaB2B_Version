package com.teamproject.comment.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comment_mentions", uniqueConstraints = @UniqueConstraint(
        name = "uk_comment_mentions_comment_member", columnNames = {"comment_id", "mentioned_member_id"}))
public class CommentMention {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private TaskComment comment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentioned_member_id", nullable = false)
    private GroupMember mentionedMember;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CommentMention() {}

    public CommentMention(TaskComment comment, GroupMember mentionedMember) {
        this.comment = comment;
        this.mentionedMember = mentionedMember;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public GroupMember getMentionedMember() { return mentionedMember; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
