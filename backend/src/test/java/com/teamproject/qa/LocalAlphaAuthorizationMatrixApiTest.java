package com.teamproject.qa;

import com.jayway.jsonpath.JsonPath;
import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
class LocalAlphaAuthorizationMatrixApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    @Test
    void memberCanReadTeamButCannotUseLeaderOnlyOperations() throws Exception {
        Team team = team("role");

        mvc.perform(get("/api/v1/groups/{groupId}", team.groupId())
                        .header("Authorization", bearer(team.member().token())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/groups/{groupId}/members", team.groupId())
                        .header("Authorization", bearer(team.member().token())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/groups/{groupId}/tasks", team.groupId())
                        .header("Authorization", bearer(team.member().token())))
                .andExpect(status().isOk());

        leaderRequired(patch("/api/v1/groups/{groupId}", team.groupId())
                .header("Authorization", bearer(team.member().token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"권한 탈취 시도\"}"));
        leaderRequired(post("/api/v1/groups/{groupId}/invitations", team.groupId())
                .header("Authorization", bearer(team.member().token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"blocked-invite@example.com\"}"));
        leaderRequired(get("/api/v1/groups/{groupId}/invitations", team.groupId())
                .header("Authorization", bearer(team.member().token())));
        leaderRequired(patch("/api/v1/groups/{groupId}/members/{memberId}/role",
                team.groupId(), team.memberId())
                .header("Authorization", bearer(team.member().token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"LEADER\"}"));
        leaderRequired(delete("/api/v1/groups/{groupId}/members/{memberId}",
                team.groupId(), team.ownerMemberId())
                .header("Authorization", bearer(team.member().token())));
        leaderRequired(post("/api/v1/groups/{groupId}/calendar-events", team.groupId())
                .header("Authorization", bearer(team.member().token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"MEETING\",\"title\":\"권한 없는 일정\","
                        + "\"startAt\":\"2030-01-01T10:00:00\","
                        + "\"endAt\":\"2030-01-01T11:00:00\",\"allDay\":false}"));

        mvc.perform(patch("/api/v1/groups/{groupId}", team.groupId())
                        .header("Authorization", bearer(team.owner().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dashboardVisibility\":\"LEADER_ONLY\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/groups/{groupId}/dashboard", team.groupId())
                        .header("Authorization", bearer(team.member().token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DASHBOARD_FORBIDDEN"));
    }

    @Test
    void outsiderAndForeignMemberIdsCannotCrossGroupBoundary() throws Exception {
        Team primary = team("scope_a");
        Team foreign = team("scope_b");
        long taskId = createTask(primary.member().token(), primary.groupId(), "교차 권한 업무");

        transition(primary.owner().token(), taskId, "ACCEPT", 0).andExpect(status().isOk());
        mvc.perform(put("/api/v1/tasks/{taskId}/assignee", taskId)
                        .header("Authorization", bearer(primary.owner().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeMemberId\":" + foreign.ownerMemberId()
                                + ",\"expectedVersion\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_NOT_FOUND"));
        mvc.perform(post("/api/v1/tasks/{taskId}/comments", taskId)
                        .header("Authorization", bearer(primary.member().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"다른 그룹 멘션\",\"mentionedMemberIds\":["
                                + foreign.memberId() + "]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_NOT_FOUND"));
        mvc.perform(get("/api/v1/tasks/{taskId}/comments", taskId)
                        .header("Authorization", bearer(primary.owner().token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        String outsiderToken = foreign.member().token();
        groupNotFound(get("/api/v1/groups/{groupId}", primary.groupId())
                .header("Authorization", bearer(outsiderToken)));
        groupNotFound(get("/api/v1/groups/{groupId}/members", primary.groupId())
                .header("Authorization", bearer(outsiderToken)));
        groupNotFound(get("/api/v1/groups/{groupId}/tasks", primary.groupId())
                .header("Authorization", bearer(outsiderToken)));
        groupNotFound(post("/api/v1/groups/{groupId}/tasks", primary.groupId())
                .header("Authorization", bearer(outsiderToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"외부 업무\"}"));
        groupNotFound(get("/api/v1/tasks/{taskId}", taskId)
                .header("Authorization", bearer(outsiderToken)));
        groupNotFound(get("/api/v1/tasks/{taskId}/histories", taskId)
                .header("Authorization", bearer(outsiderToken)));
        groupNotFound(get("/api/v1/tasks/{taskId}/checklist-items", taskId)
                .header("Authorization", bearer(outsiderToken)));
        groupNotFound(get("/api/v1/tasks/{taskId}/comments", taskId)
                .header("Authorization", bearer(outsiderToken)));
        groupNotFound(get("/api/v1/calendars/events")
                .param("groupId", String.valueOf(primary.groupId()))
                .param("from", "2029-12-01").param("to", "2030-02-01")
                .header("Authorization", bearer(outsiderToken)));
        groupNotFound(get("/api/v1/groups/{groupId}/dashboard", primary.groupId())
                .header("Authorization", bearer(outsiderToken)));
    }

    private Team team(String suffix) throws Exception {
        Account owner = account("qa_owner_" + suffix, "qa-owner-" + suffix + "@example.com");
        var result = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"권한 QA " + suffix + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        long groupId = number(result, "$.id");
        long ownerMemberId = number(result, "$.memberId");
        Account member = account("qa_member_" + suffix, "qa-member-" + suffix + "@example.com");
        GroupMember membership = members.save(GroupMember.member(groups.findById(groupId).orElseThrow(), member.user()));
        return new Team(owner, member, groupId, ownerMemberId, membership.getId());
    }

    private Account account(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "권한 QA 사용자", "password123!", code));
        User user = users.findByUsernameIgnoreCase(username).orElseThrow();
        String token = sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
        return new Account(user, token);
    }

    private long createTask(String token, long groupId, String title) throws Exception {
        var result = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return number(result, "$.id");
    }

    private ResultActions transition(String token, long taskId, String action, long version) throws Exception {
        return mvc.perform(post("/api/v1/tasks/{taskId}/transitions", taskId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\",\"expectedVersion\":" + version + "}"));
    }

    private void leaderRequired(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mvc.perform(request).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));
    }

    private void groupNotFound(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mvc.perform(request).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    private long number(org.springframework.test.web.servlet.MvcResult result, String path) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
    }

    private String bearer(String token) { return "Bearer " + token; }
    private record Account(User user, String token) {}
    private record Team(Account owner, Account member, long groupId, long ownerMemberId, long memberId) {}
}
