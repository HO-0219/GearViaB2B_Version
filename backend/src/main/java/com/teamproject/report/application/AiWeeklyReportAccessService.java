package com.teamproject.report.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.organization.application.OrganizationFeaturePolicy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * AI 주간 리포트 v7-2 권한 검증 전용 서비스 (M8).
 */
@Service
public class AiWeeklyReportAccessService {

    private final GroupMemberRepository members;
    private final OrganizationFeaturePolicy policy;

    public AiWeeklyReportAccessService(GroupMemberRepository members, OrganizationFeaturePolicy policy) {
        this.members = members;
        this.policy = policy;
    }

    public GroupMember requireActiveMember(Long groupId, Long userId) {
        return members.findByGroupIdAndUserIdAndStatus(groupId, userId, GroupMember.Status.ACTIVE)
                .orElseThrow(() -> new ApplicationException("GROUP_NOT_FOUND", HttpStatus.NOT_FOUND, "그룹을 찾을 수 없거나 접근 권한이 없습니다."));
    }

    public GroupMember requireAiWeeklyReportLeader(Long groupId, Long userId) {
        GroupMember member = requireActiveMember(groupId, userId);
        policy.requireAiWeeklyReport(userId, groupId);
        return member;
    }
}
