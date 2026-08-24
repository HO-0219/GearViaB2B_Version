package com.teamproject.organization;

import com.teamproject.aiprovider.infrastructure.openai.DynamicOpenAiSettings;
import com.teamproject.assistant.application.AiAssistantEntitlementService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.application.GroupFeaturePolicy;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.organization.application.OrganizationFeaturePolicy;
import com.teamproject.report.application.AiWeeklyReportAccessService;
import com.teamproject.user.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationFeaturePolicyTest {

    @Test
    void teamPolicyUsesOrganizationLimitsWithoutMembershipPlan() {
        Fixture fixture = Fixture.leaderInTeam();
        OrganizationFeaturePolicy policy = policy(
                mockAuthorization(fixture.member()), true, true, "test-openai-key");

        GroupFeaturePolicy.FeaturePolicyResponse response = policy.features(
                fixture.user().getId(), fixture.group().getId());

        assertThat(response.groupId()).isEqualTo(fixture.group().getId());
        assertThat(response.projectEnabled()).isTrue();
        assertThat(response.multipleChatChannels()).isTrue();
        assertThat(response.chatChannelLimit()).isEqualTo(12);
        assertThat(response.messageRetentionDays()).isEqualTo(45);
        assertThat(response.storageLimitBytes()).isEqualTo(2048);
        assertThat(response.attachmentLimitBytes()).isEqualTo(512);
    }

    @Test
    void personalPolicyKeepsTeamFeaturesUnavailableButStillUsesStorageLimits() {
        Fixture fixture = Fixture.leaderInPersonalGroup();
        OrganizationFeaturePolicy policy = policy(
                mockAuthorization(fixture.member()), true, true, "test-openai-key");

        GroupFeaturePolicy.FeaturePolicyResponse response = policy.features(
                fixture.user().getId(), fixture.group().getId());

        assertThat(response.projectEnabled()).isFalse();
        assertThat(response.multipleChatChannels()).isFalse();
        assertThat(response.chatChannelLimit()).isZero();
        assertThat(response.messageRetentionDays()).isZero();
        assertThat(response.storageLimitBytes()).isEqualTo(2048);
        assertThat(response.attachmentLimitBytes()).isEqualTo(512);
    }

    @Test
    void assistantDirectServiceRequiresAdminEnablementApiKeyAndGroupLeaderPermission() {
        Fixture leader = Fixture.leaderInTeam();
        AiAssistantEntitlementService allowed = new AiAssistantEntitlementService(policy(
                mockAuthorization(leader.member()), true, true, "test-openai-key"));

        assertThatCode(() -> allowed.require(leader.user().getId(), leader.group().getId()))
                .doesNotThrowAnyException();

        Fixture member = Fixture.memberInTeam();
        AiAssistantEntitlementService deniedByRole = new AiAssistantEntitlementService(policy(
                mockAuthorization(member.member()), true, true, "test-openai-key"));

        assertThatThrownBy(() -> deniedByRole.require(member.user().getId(), member.group().getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).code())
                        .isEqualTo("AI_GROUP_PERMISSION_REQUIRED"));

        AiAssistantEntitlementService deniedByAdminSetting = new AiAssistantEntitlementService(policy(
                mockAuthorization(leader.member()), false, true, "test-openai-key"));

        assertThatThrownBy(() -> deniedByAdminSetting.require(leader.user().getId(), leader.group().getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException applicationException = (ApplicationException) exception;
                    assertThat(applicationException.code()).isEqualTo("AI_ADMIN_CONFIGURATION_REQUIRED");
                    assertThat(applicationException.getMessage()).contains("관리자");
                });

        AiAssistantEntitlementService deniedByMissingKey = new AiAssistantEntitlementService(policy(
                mockAuthorization(leader.member()), true, true, " "));

        assertThatThrownBy(() -> deniedByMissingKey.require(leader.user().getId(), leader.group().getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException applicationException = (ApplicationException) exception;
                    assertThat(applicationException.code()).isEqualTo("AI_ADMIN_CONFIGURATION_REQUIRED");
                    assertThat(applicationException.getMessage()).contains("관리자");
                });
    }

    @Test
    void aiWeeklyReportDirectServiceCannotBypassOrganizationPolicy() {
        Fixture leader = Fixture.leaderInTeam();
        GroupMemberRepository members = mock(GroupMemberRepository.class);
        when(members.findByGroupIdAndUserIdAndStatus(
                leader.group().getId(), leader.user().getId(), GroupMember.Status.ACTIVE))
                .thenReturn(Optional.of(leader.member()));
        AiWeeklyReportAccessService allowed = new AiWeeklyReportAccessService(
                members, policy(mockAuthorization(leader.member()), true, true, "test-openai-key"));

        assertThatCode(() -> allowed.requireAiWeeklyReportLeader(
                leader.group().getId(), leader.user().getId())).doesNotThrowAnyException();

        Fixture member = Fixture.memberInTeam();
        GroupMemberRepository memberRepository = mock(GroupMemberRepository.class);
        when(memberRepository.findByGroupIdAndUserIdAndStatus(
                member.group().getId(), member.user().getId(), GroupMember.Status.ACTIVE))
                .thenReturn(Optional.of(member.member()));
        AiWeeklyReportAccessService denied = new AiWeeklyReportAccessService(
                memberRepository, policy(mockAuthorization(member.member()), true, true, "test-openai-key"));

        assertThatThrownBy(() -> denied.requireAiWeeklyReportLeader(
                member.group().getId(), member.user().getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).code())
                        .isEqualTo("AI_GROUP_PERMISSION_REQUIRED"));
    }

    private OrganizationFeaturePolicy policy(GroupAuthorization authorization,
            boolean assistantEnabled, boolean reportEnabled, String openAiKey) {
        DynamicOpenAiSettings openAi = mock(DynamicOpenAiSettings.class);
        when(openAi.assistantEnabled()).thenReturn(assistantEnabled);
        when(openAi.reportEnabled()).thenReturn(reportEnabled);
        when(openAi.hasApiKey()).thenReturn(openAiKey != null && !openAiKey.isBlank());
        return new OrganizationFeaturePolicy(authorization, 12, 45, 2048, 512, openAi);
    }

    private static GroupAuthorization mockAuthorization(GroupMember member) {
        GroupAuthorization authorization = mock(GroupAuthorization.class);
        when(authorization.requireActiveMember(member.getGroup().getId(), member.getUser().getId()))
                .thenReturn(member);
        return authorization;
    }

    private record Fixture(User user, Group group, GroupMember member) {
        static Fixture leaderInTeam() {
            User user = user("leader");
            Group group = Group.team("정책 팀", null, "Asia/Seoul", user);
            setId(group, 101L);
            setId(user, 201L);
            return new Fixture(user, group, GroupMember.leader(group, user));
        }

        static Fixture memberInTeam() {
            User user = user("member");
            Group group = Group.team("정책 팀", null, "Asia/Seoul", user);
            setId(group, 102L);
            setId(user, 202L);
            return new Fixture(user, group, GroupMember.member(group, user));
        }

        static Fixture leaderInPersonalGroup() {
            User user = user("personal");
            Group group = Group.personal(user);
            setId(group, 103L);
            setId(user, 203L);
            return new Fixture(user, group, GroupMember.leader(group, user));
        }

        private static User user(String prefix) {
            return new User(prefix + "_user", prefix + "@example.com", "hash",
                    prefix + " user", true);
        }

        private static void setId(Object target, Long id) {
            org.springframework.test.util.ReflectionTestUtils.setField(target, "id", id);
        }
    }
}
