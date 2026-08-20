package com.teamproject.group;

import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.authentication.infrastructure.crypto.HashService;
import com.teamproject.group.application.GroupService;
import com.teamproject.group.application.dto.GroupDtos.CreateGroupRequest;
import com.teamproject.group.domain.*;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class GroupMemberLifecycleApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupService groupService;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired GroupInvitationRepository invitations;
    @Autowired HashService hashes;

    @Test
    void leadersTransferRoleAndLastLeaderCannotBeDemotedOrLeave() throws Exception {
        Account owner = account("role_owner", "role-owner@example.com");
        Account member = account("role_member", "role-member@example.com");
        long teamId = team(owner.user(), "역할 변경 팀");
        GroupMember ownerMembership = membership(teamId, owner.user());
        GroupMember memberMembership = members.save(GroupMember.member(groups.findById(teamId).orElseThrow(), member.user()));

        mvc.perform(patch("/api/v1/groups/{groupId}/members/{memberId}/role", teamId, memberMembership.getId())
                        .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"LEADER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("LEADER"));
        mvc.perform(patch("/api/v1/groups/{groupId}/members/{memberId}/role", teamId, ownerMembership.getId())
                        .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBER"));
        mvc.perform(patch("/api/v1/groups/{groupId}/members/{memberId}/role", teamId, memberMembership.getId())
                        .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_GROUP_LEADER"));
        mvc.perform(delete("/api/v1/groups/{groupId}/members/me", teamId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_GROUP_LEADER"));
    }

    @Test
    void memberLeavesAndRemovedMemberLosesAccess() throws Exception {
        Account owner = account("exit_owner", "exit-owner@example.com");
        Account leaving = account("exit_leaving", "exit-leaving@example.com");
        Account removed = account("exit_removed", "exit-removed@example.com");
        long teamId = team(owner.user(), "탈퇴 테스트 팀");
        members.save(GroupMember.member(groups.findById(teamId).orElseThrow(), leaving.user()));
        GroupMember removedMembership = members.save(GroupMember.member(groups.findById(teamId).orElseThrow(), removed.user()));

        mvc.perform(delete("/api/v1/groups/{groupId}/members/me", teamId)
                        .header("Authorization", bearer(leaving))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/groups/{groupId}", teamId).header("Authorization", bearer(leaving)))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/groups/{groupId}/members/{memberId}", teamId, removedMembership.getId())
                        .header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/groups/{groupId}", teamId).header("Authorization", bearer(removed)))
                .andExpect(status().isNotFound());
    }

    @Test
    void removedMemberCanRejoinWithNewInvitationAndPersonalMembershipCannotChange() throws Exception {
        Account owner = account("rejoin_owner", "rejoin-owner@example.com");
        Account member = account("rejoin_member", "rejoin-member@example.com");
        long teamId = team(owner.user(), "재가입 테스트 팀");
        Group group = groups.findById(teamId).orElseThrow();
        GroupMember oldMembership = members.save(GroupMember.member(group, member.user()));
        long membershipId = oldMembership.getId();

        mvc.perform(delete("/api/v1/groups/{groupId}/members/{memberId}", teamId, membershipId)
                        .header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        String rawToken = "rejoin-invitation-token";
        invitations.save(new GroupInvitation(group, member.user().getEmail(), membership(teamId, owner.user()),
                hashes.sha256(rawToken), LocalDateTime.now().plusHours(1)));
        mvc.perform(post("/api/v1/group-invitations/{token}/accept", rawToken)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(membershipId))
                .andExpect(jsonPath("$.role").value("MEMBER"));
        assertThat(members.findById(membershipId).orElseThrow().getStatus()).isEqualTo(GroupMember.Status.ACTIVE);

        long personalId = groupService.list(owner.user().getId()).stream()
                .filter(value -> value.type().equals("PERSONAL")).findFirst().orElseThrow().id();
        mvc.perform(delete("/api/v1/groups/{groupId}/members/me", personalId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERSONAL_GROUP_RESTRICTED"));
    }

    private Account account(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "멤버 사용자", "password123!", code));
        User user = users.findByUsernameIgnoreCase(username).orElseThrow();
        String token = sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
        return new Account(user, token);
    }

    private long team(User owner, String name) {
        return groupService.createTeam(owner.getId(), new CreateGroupRequest(name, null, "Asia/Seoul")).id();
    }

    private GroupMember membership(long groupId, User user) {
        return members.findByGroupIdAndUserIdAndStatus(groupId, user.getId(), GroupMember.Status.ACTIVE).orElseThrow();
    }

    private String bearer(Account account) { return "Bearer " + account.token(); }
    private record Account(User user, String token) {}
}
