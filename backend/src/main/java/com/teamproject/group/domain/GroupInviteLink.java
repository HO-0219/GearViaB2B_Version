package com.teamproject.group.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_invite_links", uniqueConstraints =
        @UniqueConstraint(name = "uk_group_invite_links_token_hash", columnNames = "token_hash"))
public class GroupInviteLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_member_id", nullable = false)
    private GroupMember createdBy;
    @Column(nullable = false, length = 64)
    private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    @Column(nullable = false)
    private int usedCount;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected GroupInviteLink() {}

    public GroupInviteLink(Group group, GroupMember createdBy, String tokenHash, LocalDateTime expiresAt) {
        this.group = group;
        this.createdBy = createdBy;
        this.tokenHash = tokenHash;
        this.status = Status.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isUsable(String hash, LocalDateTime now) {
        return status == Status.ACTIVE && expiresAt.isAfter(now) && tokenHash.equals(hash);
    }
    public boolean isActiveAt(LocalDateTime now) { return status == Status.ACTIVE && expiresAt.isAfter(now); }
    public void use() { usedCount++; }
    public void revoke() { status = Status.REVOKED; }
    public void expire() { if (status == Status.ACTIVE) status = Status.EXPIRED; }
    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public GroupMember getCreatedBy() { return createdBy; }
    public Status getStatus() { return status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public int getUsedCount() { return usedCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public enum Status { ACTIVE, REVOKED, EXPIRED }
}
