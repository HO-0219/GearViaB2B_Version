package com.teamproject.group.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GroupAuthorization {
    private final GroupMemberRepository members;

    public GroupAuthorization(GroupMemberRepository members) { this.members = members; }

    public GroupMember requireActiveMember(Long groupId, Long userId) {
        return members.findByGroupIdAndUserIdAndStatus(groupId, userId, GroupMember.Status.ACTIVE)
                .orElseThrow(() -> new ApplicationException("GROUP_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "그룹을 찾을 수 없습니다."));
    }

    public GroupMember requireLeader(Long groupId, Long userId) {
        GroupMember member = requireActiveMember(groupId, userId);
        if (member.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("GROUP_LEADER_REQUIRED", HttpStatus.FORBIDDEN,
                    "그룹 팀장 권한이 필요합니다.");
        }
        return member;
    }

    public GroupMember requireActiveMemberById(Long groupId, Long memberId) {
        return members.findByIdAndGroupIdAndStatus(memberId, groupId, GroupMember.Status.ACTIVE)
                .orElseThrow(() -> new ApplicationException("GROUP_MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "활성 멤버를 찾을 수 없습니다."));
    }
}
