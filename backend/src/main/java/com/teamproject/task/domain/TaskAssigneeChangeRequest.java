package com.teamproject.task.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_assignee_change_requests")
public class TaskAssigneeChangeRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "task_id") private Task task;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requested_by_member_id") private GroupMember requestedBy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "proposed_assignee_member_id") private GroupMember proposedAssignee;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by_member_id") private GroupMember reviewedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(length = 500) private String reason;
    @Column(length = 500) private String reviewNote;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    @Version private long version;
    protected TaskAssigneeChangeRequest() {}
    public TaskAssigneeChangeRequest(Task task, GroupMember requestedBy, GroupMember proposedAssignee, String reason) {
        this.task = task; this.requestedBy = requestedBy; this.proposedAssignee = proposedAssignee;
        this.reason = reason; this.status = Status.PENDING; this.createdAt = LocalDateTime.now();
    }
    public void review(GroupMember reviewer, boolean approve, String note) {
        this.reviewedBy = reviewer; this.status = approve ? Status.APPROVED : Status.REJECTED;
        this.reviewNote = note; this.reviewedAt = LocalDateTime.now();
    }
    public Long getId(){return id;} public Task getTask(){return task;} public GroupMember getRequestedBy(){return requestedBy;}
    public GroupMember getProposedAssignee(){return proposedAssignee;} public GroupMember getReviewedBy(){return reviewedBy;}
    public Status getStatus(){return status;} public String getReason(){return reason;} public String getReviewNote(){return reviewNote;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getReviewedAt(){return reviewedAt;} public long getVersion(){return version;}
    public enum Status { PENDING, APPROVED, REJECTED }
}
