package com.teamproject.comment.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comment_revisions")
public class CommentRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private TaskComment comment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edited_by_member_id", nullable = false)
    private GroupMember editedBy;
    @Column(name = "previous_content", nullable = false, length = 2000)
    private String previousContent;
    @Column(name = "new_content", nullable = false, length = 2000)
    private String newContent;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CommentRevision() {}

    public CommentRevision(TaskComment comment, GroupMember editedBy, String previousContent, String newContent) {
        this.comment = comment;
        this.editedBy = editedBy;
        this.previousContent = previousContent;
        this.newContent = newContent;
        this.createdAt = LocalDateTime.now();
    }
}
