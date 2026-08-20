package com.teamproject.project.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_issue_checklist_items", indexes = @Index(
        name = "idx_project_issue_checklist_order", columnList = "issue_id,sort_order,id"))
public class ProjectIssueChecklistItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "issue_id") private ProjectIssue issue;
    @Column(nullable = false, length = 500) private String content;
    @Column(nullable = false) private boolean completed;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "completed_by_member_id") private GroupMember completedBy;
    private LocalDateTime completedAt;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @Version private long version;

    protected ProjectIssueChecklistItem() {}
    public ProjectIssueChecklistItem(ProjectIssue issue, String content, int sortOrder) {
        this.issue = issue; this.content = content; this.sortOrder = sortOrder;
        this.createdAt = LocalDateTime.now(); this.updatedAt = createdAt;
    }
    public void update(String content, Boolean completed, Integer sortOrder, GroupMember actor) {
        if (content != null) this.content = content;
        if (sortOrder != null) this.sortOrder = sortOrder;
        if (completed != null && completed != this.completed) {
            this.completed = completed; this.completedBy = completed ? actor : null;
            this.completedAt = completed ? LocalDateTime.now() : null;
        }
        this.updatedAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public ProjectIssue getIssue() { return issue; }
    public String getContent() { return content; }
    public boolean isCompleted() { return completed; }
    public GroupMember getCompletedBy() { return completedBy; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public int getSortOrder() { return sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
