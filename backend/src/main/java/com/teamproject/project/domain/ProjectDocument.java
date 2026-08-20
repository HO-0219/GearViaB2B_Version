package com.teamproject.project.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_documents", indexes = {
        @Index(name = "idx_project_documents_location", columnList = "project_id,issue_node_id,deleted_at,created_at,id"),
        @Index(name = "idx_project_documents_checksum", columnList = "project_id,issue_node_id,checksum_sha256,deleted_at")
})
public class ProjectDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "issue_node_id") private ProjectIssue issueNode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_member_id") private GroupMember createdBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Type documentType;
    @Column(nullable = false, length = 160) private String title;
    @Column(length = 1000) private String externalUrl;
    @Column(length = 500, unique = true) private String storageKey;
    @Column(length = 255) private String originalFilename;
    @Column(length = 120) private String contentType;
    private Long sizeBytes;
    @Column(length = 64, columnDefinition = "char(64)") private String checksumSha256;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

    protected ProjectDocument() {}
    private ProjectDocument(Project project, ProjectIssue issueNode, GroupMember createdBy, Type type, String title) {
        this.project = project; this.issueNode = issueNode; this.createdBy = createdBy;
        this.documentType = type; this.title = title; this.createdAt = LocalDateTime.now();
    }
    public static ProjectDocument link(Project project, ProjectIssue location, GroupMember member,
            String title, String url) {
        ProjectDocument value = new ProjectDocument(project, location, member, Type.LINK, title);
        value.externalUrl = url; return value;
    }
    public static ProjectDocument file(Project project, ProjectIssue location, GroupMember member,
            String title, String key, String filename, String contentType, long size, String checksum) {
        ProjectDocument value = new ProjectDocument(project, location, member, Type.FILE, title);
        value.storageKey = key; value.originalFilename = filename; value.contentType = contentType;
        value.sizeBytes = size; value.checksumSha256 = checksum; return value;
    }
    public void delete() { if (deletedAt == null) deletedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public Project getProject() { return project; }
    public ProjectIssue getIssueNode() { return issueNode; }
    public GroupMember getCreatedBy() { return createdBy; }
    public Type getDocumentType() { return documentType; }
    public String getTitle() { return title; }
    public String getExternalUrl() { return externalUrl; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public enum Type { LINK, FILE }
}
