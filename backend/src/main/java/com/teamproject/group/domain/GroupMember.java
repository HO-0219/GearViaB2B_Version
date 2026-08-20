package com.teamproject.group.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_members", uniqueConstraints =
        @UniqueConstraint(name = "uk_group_members_group_user", columnNames = {"group_id", "user_id"}))
public class GroupMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(nullable = false)
    private LocalDateTime joinedAt;

    protected GroupMember() {}

    private GroupMember(Group group, User user, Role role) {
        this.group = group;
        this.user = user;
        this.role = role;
        this.status = Status.ACTIVE;
        this.joinedAt = LocalDateTime.now();
    }

    public static GroupMember leader(Group group, User user) {
        return new GroupMember(group, user, Role.LEADER);
    }

    public static GroupMember member(Group group, User user) {
        return new GroupMember(group, user, Role.MEMBER);
    }

    public void changeRole(Role role) { this.role = role; }
    public void leave() { this.status = Status.LEFT; }
    public void remove() { this.status = Status.REMOVED; }
    public void reactivateAsMember() {
        this.role = Role.MEMBER;
        this.status = Status.ACTIVE;
        this.joinedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public User getUser() { return user; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public LocalDateTime getJoinedAt() { return joinedAt; }

    public enum Role { LEADER, MEMBER }
    public enum Status { ACTIVE, LEFT, REMOVED }
}
