package com.teamproject.group.domain;

import com.teamproject.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    @Query("select distinct m.user from GroupMember m "
            + "where m.role = com.teamproject.group.domain.GroupMember.Role.LEADER "
            + "and m.status = com.teamproject.group.domain.GroupMember.Status.ACTIVE "
            + "and m.group.type = com.teamproject.group.domain.Group.Type.TEAM "
            + "and m.user.status = com.teamproject.user.domain.User.Status.ACTIVE")
    List<User> findDistinctActiveTeamLeaderUsers();
    List<GroupMember> findAllByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(
            Long userId, GroupMember.Status status);
    @EntityGraph(attributePaths = "group")
    List<GroupMember> findByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(
            Long userId, GroupMember.Status status, Pageable pageable);
    long countByUserIdAndGroupType(Long userId, Group.Type type);
    @EntityGraph(attributePaths = "group")
    Optional<GroupMember> findByGroupIdAndUserIdAndStatus(Long groupId, Long userId, GroupMember.Status status);
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
    Optional<GroupMember> findByIdAndGroupIdAndStatus(Long id, Long groupId, GroupMember.Status status);
    @EntityGraph(attributePaths = "user")
    List<GroupMember> findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(Long groupId, GroupMember.Status status);
    long countByGroupIdAndStatus(Long groupId, GroupMember.Status status);
    @Query("select gm.group.id as groupId, count(gm) as memberCount from GroupMember gm "
            + "where gm.group.id in :groupIds and gm.status = :status group by gm.group.id")
    List<GroupMemberCount> countByGroupIdsAndStatus(@Param("groupIds") Collection<Long> groupIds,
            @Param("status") GroupMember.Status status);
    long countByGroupIdAndRoleAndStatus(Long groupId, GroupMember.Role role, GroupMember.Status status);
    interface GroupMemberCount {
        Long getGroupId();
        long getMemberCount();
    }
}
