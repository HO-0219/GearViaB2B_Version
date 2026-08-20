package com.teamproject.chat.domain;

import com.teamproject.group.domain.*;
import com.teamproject.project.domain.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_channels", uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_channels_group_key", columnNames = {"group_id", "channel_key"}),
        indexes = @Index(name = "idx_chat_channels_group_active", columnList = "group_id,archived_at,created_at,id"))
public class ChatChannel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id") private Group group;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id") private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "issue_node_id") private ProjectIssue issueNode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_member_id") private GroupMember createdBy;
    @Column(name = "channel_key", nullable = false, length = 80) private String channelKey;
    @Column(nullable = false, length = 80) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "channel_type", nullable = false, length = 20) private Type channelType;
    private LocalDateTime archivedAt;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected ChatChannel() {}
    public ChatChannel(Group group, Project project, ProjectIssue issueNode, GroupMember createdBy,
            String key, String name, Type type) {
        this.group = group; this.project = project; this.issueNode = issueNode; this.createdBy = createdBy;
        this.channelKey = key; this.name = name; this.channelType = type;
        this.createdAt = LocalDateTime.now(); this.updatedAt = createdAt;
    }
    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public Project getProject() { return project; }
    public ProjectIssue getIssueNode() { return issueNode; }
    public GroupMember getCreatedBy() { return createdBy; }
    public String getName() { return name; }
    public Type getChannelType() { return channelType; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public enum Type { GENERAL, TOPIC }
}
