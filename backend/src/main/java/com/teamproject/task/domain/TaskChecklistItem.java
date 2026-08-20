package com.teamproject.task.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_checklist_items")
public class TaskChecklistItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;
    @Column(nullable = false, length = 300)
    private String content;
    @Column(nullable = false)
    private boolean completed;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_member_id")
    private GroupMember completedBy;
    private LocalDateTime completedAt;
    @Column(nullable = false)
    private int sortOrder;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Version
    private long version;

    protected TaskChecklistItem() {}

    public TaskChecklistItem(Task task, String content, int sortOrder) {
        this.task = task;
        this.content = content;
        this.sortOrder = sortOrder;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    public void update(String content, Integer sortOrder) {
        if (content != null) this.content = content;
        if (sortOrder != null) this.sortOrder = sortOrder;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeCompletion(boolean completed, GroupMember actor) {
        this.completed = completed;
        this.completedBy = completed ? actor : null;
        this.completedAt = completed ? LocalDateTime.now() : null;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Task getTask() { return task; }
    public String getContent() { return content; }
    public boolean isCompleted() { return completed; }
    public GroupMember getCompletedBy() { return completedBy; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public int getSortOrder() { return sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
