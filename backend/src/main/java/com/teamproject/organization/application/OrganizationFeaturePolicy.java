package com.teamproject.organization.application;

import com.teamproject.aiprovider.infrastructure.openai.DynamicOpenAiSettings;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.application.GroupFeaturePolicy.FeaturePolicyResponse;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationFeaturePolicy {
    private final GroupAuthorization authorization;
    private final int chatChannelLimit;
    private final int messageRetentionDays;
    private final long storageLimitBytes;
    private final long attachmentLimitBytes;
    private final DynamicOpenAiSettings openAi;

    public OrganizationFeaturePolicy(GroupAuthorization authorization,
            @Value("${app.organization.chat.channel-limit:50}") int chatChannelLimit,
            @Value("${app.organization.chat.message-retention-days:365}") int messageRetentionDays,
            @Value("${app.organization.storage.total-bytes:5368709120}") long storageLimitBytes,
            @Value("${app.organization.storage.attachment-limit-bytes:104857600}") long attachmentLimitBytes,
            DynamicOpenAiSettings openAi) {
        this.authorization = authorization;
        this.chatChannelLimit = chatChannelLimit;
        this.messageRetentionDays = messageRetentionDays;
        this.storageLimitBytes = storageLimitBytes;
        this.attachmentLimitBytes = attachmentLimitBytes;
        this.openAi = openAi;
    }

    @Transactional(readOnly = true)
    public FeaturePolicyResponse features(Long userId, Long groupId) {
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        Group group = member.getGroup();
        boolean team = group.getType() == Group.Type.TEAM;
        return new FeaturePolicyResponse(groupId, team, team, team ? chatChannelLimit : 0,
                team ? messageRetentionDays : 0, storageLimitBytes, attachmentLimitBytes);
    }

    @Transactional(readOnly = true)
    public GroupMember requireAiAssistant(Long userId, Long groupId) {
        GroupMember member = requireConfiguredAi(userId, groupId, openAi.assistantEnabled());
        requireLeader(member);
        return member;
    }

    @Transactional(readOnly = true)
    public GroupMember requireAiWeeklyReport(Long userId, Long groupId) {
        GroupMember member = requireConfiguredAi(userId, groupId, openAi.reportEnabled());
        requireLeader(member);
        return member;
    }

    @Transactional(readOnly = true)
    public GroupMember requireReportScheduling(Long userId, Long groupId) {
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        requireTeam(member);
        requireLeader(member);
        return member;
    }

    public boolean reportSchedulingEnabled(Group group) {
        return group.getType() == Group.Type.TEAM;
    }

    private GroupMember requireConfiguredAi(Long userId, Long groupId, boolean adminEnabled) {
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        requireTeam(member);
        if (!adminEnabled || !openAi.hasApiKey()) {
            throw new ApplicationException("AI_ADMIN_CONFIGURATION_REQUIRED", HttpStatus.FORBIDDEN,
                    "AI 기능은 서버 관리자가 기능을 활성화하고 OpenAI API 키를 설정한 뒤 사용할 수 있습니다. 관리자에게 설정을 요청해 주세요.");
        }
        return member;
    }

    private void requireTeam(GroupMember member) {
        if (member.getGroup().getType() != Group.Type.TEAM) {
            throw new ApplicationException("AI_GROUP_PERMISSION_REQUIRED", HttpStatus.FORBIDDEN,
                    "AI 기능은 관리자 정책에서 허용된 팀 그룹에서 사용할 수 있습니다.");
        }
    }

    private void requireLeader(GroupMember member) {
        if (member.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("AI_GROUP_PERMISSION_REQUIRED", HttpStatus.FORBIDDEN,
                    "AI 기능은 팀장 권한이 있는 사용자만 사용할 수 있습니다. 권한이 필요하면 관리자에게 요청해 주세요.");
        }
    }
}
