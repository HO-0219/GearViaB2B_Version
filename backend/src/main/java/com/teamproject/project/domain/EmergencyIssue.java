package com.teamproject.project.domain;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "emergency_issues")
public class EmergencyIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id") private Group group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_member_id") private GroupMember createdBy;
    @Column(nullable = false, length = 160) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Audience audience;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(length = 500) private String imageUrl;
    private LocalDateTime resolvedAt;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @Version private long version;
    protected EmergencyIssue() {}
    public EmergencyIssue(Group group, Project project, GroupMember createdBy, String title,
            String description, Audience audience) {
        this.group = group; this.project = project; this.createdBy = createdBy; this.title = title;
        this.description = description; this.audience = audience; this.status = Status.OPEN;
        this.createdAt = LocalDateTime.now(); this.updatedAt = createdAt;
    }
    public void changeStatus(Status status) {
        this.status = status; this.resolvedAt = status == Status.RESOLVED ? LocalDateTime.now() : null;
        this.updatedAt = LocalDateTime.now();
    }
    public void attachImage(String imageUrl) { this.imageUrl = imageUrl; this.updatedAt = LocalDateTime.now(); }
    public Long getId(){return id;} public Group getGroup(){return group;} public Project getProject(){return project;}
    public GroupMember getCreatedBy(){return createdBy;} public String getTitle(){return title;}
    public String getDescription(){return description;} public Audience getAudience(){return audience;}
    public Status getStatus(){return status;} public String getImageUrl(){return imageUrl;}
    public LocalDateTime getResolvedAt(){return resolvedAt;} public LocalDateTime getCreatedAt(){return createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
    public enum Audience { PROJECT_PARTICIPANTS, WHOLE_TEAM }
    public enum Status { OPEN, RESOLVED }
}
