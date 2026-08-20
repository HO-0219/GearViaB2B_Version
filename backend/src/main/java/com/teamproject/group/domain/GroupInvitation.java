package com.teamproject.group.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_invitations", uniqueConstraints =
        @UniqueConstraint(name = "uk_group_invitations_token_hash", columnNames = "token_hash"))
public class GroupInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;
    @Column(nullable = false, length = 255)
    private String email;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_member_id", nullable = false)
    private GroupMember invitedBy;
    @Column(nullable = false, length = 64)
    private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected GroupInvitation() {}

    public GroupInvitation(Group group, String email, GroupMember invitedBy,
            String tokenHash, LocalDateTime expiresAt) {
        this.group = group;
        this.email = email;
        this.invitedBy = invitedBy;
        this.tokenHash = tokenHash;
        this.status = Status.PENDING;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isUsable(String hash, LocalDateTime now) {
        return status == Status.PENDING && expiresAt.isAfter(now) && tokenHash.equals(hash);
    }
    public boolean isPendingAt(LocalDateTime now) { return status == Status.PENDING && expiresAt.isAfter(now); }
    public void accept(LocalDateTime now) { status = Status.ACCEPTED; acceptedAt = now; }
    public void cancel() { status = Status.CANCELLED; }
    public void expire() { if (status == Status.PENDING) status = Status.EXPIRED; }
    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public String getEmail() { return email; }
    public GroupMember getInvitedBy() { return invitedBy; }
    public Status getStatus() { return status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public enum Status { PENDING, ACCEPTED, CANCELLED, EXPIRED }
}
