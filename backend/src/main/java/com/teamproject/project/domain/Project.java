package com.teamproject.project.domain;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects", indexes = @Index(name = "idx_projects_group_status_updated",
        columnList = "group_id,status,updated_at,id"))
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id") private Group group;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "lead_member_id") private GroupMember lead;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_member_id") private GroupMember createdBy;
    @Column(nullable = false, length = 120) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    private LocalDate startDate;
    private LocalDate dueDate;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @Version private long version;

    protected Project() {}

    public Project(Group group, GroupMember createdBy, GroupMember lead, String name,
            String description, LocalDate startDate, LocalDate dueDate) {
        this.group = group;
        this.createdBy = createdBy;
        this.lead = lead;
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.status = Status.PLANNED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    public void update(String name, String description, GroupMember lead, Status status,
            LocalDate startDate, LocalDate dueDate) {
        this.name = name;
        this.description = description;
        this.lead = lead;
        this.status = status;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.updatedAt = LocalDateTime.now();
    }

    public void archive() { this.status = Status.ARCHIVED; this.updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public GroupMember getLead() { return lead; }
    public GroupMember getCreatedBy() { return createdBy; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public enum Status { PLANNED, ACTIVE, ON_HOLD, COMPLETED, ARCHIVED }
}
