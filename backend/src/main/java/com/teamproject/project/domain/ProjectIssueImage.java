package com.teamproject.project.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_issue_images", uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_issue_images_storage_key", columnNames = "storage_key"),
        @UniqueConstraint(name = "uk_project_issue_images_checksum", columnNames = {"issue_id", "checksum_sha256"})
}, indexes = @Index(name = "idx_project_issue_images_order", columnList = "issue_id,sort_order,id"))
public class ProjectIssueImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "issue_id") private ProjectIssue issue;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploaded_by_member_id") private GroupMember uploadedBy;
    @Column(nullable = false, length = 500) private String storageKey;
    @Column(nullable = false, length = 255) private String originalFilename;
    @Column(nullable = false, length = 100) private String contentType;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 64, columnDefinition = "char(64)") private String checksumSha256;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;

    protected ProjectIssueImage() {}
    public ProjectIssueImage(ProjectIssue issue, GroupMember uploadedBy, String storageKey,
            String originalFilename, String contentType, long sizeBytes, String checksumSha256, int sortOrder) {
        this.issue = issue; this.uploadedBy = uploadedBy; this.storageKey = storageKey;
        this.originalFilename = originalFilename; this.contentType = contentType; this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256; this.sortOrder = sortOrder; this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public ProjectIssue getIssue() { return issue; }
    public GroupMember getUploadedBy() { return uploadedBy; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getSortOrder() { return sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
