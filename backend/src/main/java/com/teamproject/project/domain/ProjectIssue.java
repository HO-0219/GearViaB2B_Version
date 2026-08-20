package com.teamproject.project.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_issue_nodes", indexes = {
        @Index(name = "idx_project_issue_nodes_project_parent", columnList = "project_id,parent_id,archived_at,sort_order,id"),
        @Index(name = "idx_project_issue_nodes_assignee", columnList = "assignee_member_id,status,archived_at")
})
public class ProjectIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_id") private ProjectIssue parent;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignee_member_id") private GroupMember assignee;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_member_id") private GroupMember createdBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Level level;
    @Column(nullable = false, length = 160) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private int sortOrder;
    private LocalDate dueDate;
    private LocalDateTime archivedAt;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @Version private long version;

    protected ProjectIssue() {}

    public ProjectIssue(Project project, ProjectIssue parent, GroupMember assignee, GroupMember createdBy,
            Level level, String title, String description, int sortOrder, LocalDate dueDate) {
        this.project = project; this.parent = parent; this.assignee = assignee; this.createdBy = createdBy;
        this.level = level; this.title = title; this.description = description; this.sortOrder = sortOrder;
        this.dueDate = dueDate; this.status = Status.OPEN; this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    public void update(String title, String description, GroupMember assignee, Status status,
            int sortOrder, LocalDate dueDate) {
        this.title = title; this.description = description; this.assignee = assignee; this.status = status;
        this.sortOrder = sortOrder; this.dueDate = dueDate; this.updatedAt = LocalDateTime.now();
    }

    public void archive() { if (archivedAt == null) { archivedAt = LocalDateTime.now(); updatedAt = archivedAt; } }
    public Long getId() { return id; }
    public Project getProject() { return project; }
    public ProjectIssue getParent() { return parent; }
    public GroupMember getAssignee() { return assignee; }
    public GroupMember getCreatedBy() { return createdBy; }
    public Level getLevel() { return level; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public int getSortOrder() { return sortOrder; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public enum Level { MAJOR, MIDDLE, ISSUE }
    public enum Status { OPEN, IN_PROGRESS, BLOCKED, DONE }
}
