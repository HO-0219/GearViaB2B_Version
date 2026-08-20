package com.teamproject.task.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_status_histories")
public class TaskStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private Task.Status fromStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Task.Status toStatus;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_member_id", nullable = false)
    private GroupMember changedBy;
    @Column(length = 500)
    private String reason;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TaskStatusHistory() {}
    public TaskStatusHistory(Task task, Task.Status fromStatus, Task.Status toStatus,
            GroupMember changedBy, String reason) {
        this.task = task;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Task getTask() { return task; }
    public Task.Status getFromStatus() { return fromStatus; }
    public Task.Status getToStatus() { return toStatus; }
    public GroupMember getChangedBy() { return changedBy; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
