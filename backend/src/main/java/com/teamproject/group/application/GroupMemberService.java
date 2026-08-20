package com.teamproject.group.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.dto.GroupDtos.MemberResponse;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupMemberService {
    private final GroupAuthorization authorization;
    private final GroupRepository groups;
    private final GroupMemberRepository members;

    public GroupMemberService(GroupAuthorization authorization, GroupRepository groups,
            GroupMemberRepository members) {
        this.authorization = authorization;
        this.groups = groups;
        this.members = members;
    }

    @Transactional
    public MemberResponse changeRole(Long userId, Long groupId, Long memberId, String rawRole) {
        lockTeam(groupId);
        authorization.requireLeader(groupId, userId);
        GroupMember target = activeMember(groupId, memberId);
        GroupMember.Role role = role(rawRole);
        if (target.getRole() == role) return response(target);
        if (target.getRole() == GroupMember.Role.LEADER && role == GroupMember.Role.MEMBER) {
            requireAnotherLeader(groupId);
        }
        target.changeRole(role);
        return response(target);
    }

    @Transactional
    public void remove(Long userId, Long groupId, Long memberId) {
        lockTeam(groupId);
        GroupMember actor = authorization.requireLeader(groupId, userId);
        GroupMember target = activeMember(groupId, memberId);
        if (actor.getId().equals(target.getId())) {
            throw new ApplicationException("MEMBER_SELF_REMOVE_NOT_ALLOWED", HttpStatus.BAD_REQUEST,
                    "본인 탈퇴 API를 이용해 주세요.");
        }
        if (target.getRole() == GroupMember.Role.LEADER) requireAnotherLeader(groupId);
        target.remove();
    }

    @Transactional
    public void leave(Long userId, Long groupId) {
        lockTeam(groupId);
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        if (member.getRole() == GroupMember.Role.LEADER) requireAnotherLeader(groupId);
        member.leave();
    }

    private Group lockTeam(Long groupId) {
        Group group = groups.findByIdForUpdate(groupId).orElseThrow(() ->
                new ApplicationException("GROUP_NOT_FOUND", HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다."));
        if (group.getType() != Group.Type.TEAM) {
            throw new ApplicationException("PERSONAL_GROUP_RESTRICTED", HttpStatus.BAD_REQUEST,
                    "개인 그룹의 멤버십은 변경할 수 없습니다.");
        }
        return group;
    }

    private GroupMember activeMember(Long groupId, Long memberId) {
        return members.findByIdAndGroupIdAndStatus(memberId, groupId, GroupMember.Status.ACTIVE)
                .orElseThrow(() -> new ApplicationException("GROUP_MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "활성 멤버를 찾을 수 없습니다."));
    }

    private void requireAnotherLeader(Long groupId) {
        if (members.countByGroupIdAndRoleAndStatus(groupId, GroupMember.Role.LEADER,
                GroupMember.Status.ACTIVE) <= 1) {
            throw new ApplicationException("LAST_GROUP_LEADER", HttpStatus.CONFLICT,
                    "마지막 팀장은 역할을 변경하거나 그룹을 떠날 수 없습니다.");
        }
    }

    private GroupMember.Role role(String value) {
        try {
            return GroupMember.Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException("GROUP_ROLE_INVALID", HttpStatus.BAD_REQUEST,
                    "올바른 그룹 역할을 입력해 주세요.");
        }
    }

    private MemberResponse response(GroupMember value) {
        return new MemberResponse(value.getId(), value.getUser().getId(), value.getUser().getNickname(),
                value.getUser().getProfileImageUrl(), value.getRole().name(), value.getStatus().name(), value.getJoinedAt());
    }
}
