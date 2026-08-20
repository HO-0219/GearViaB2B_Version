package com.teamproject.dashboard;

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
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class DashboardApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    @Test
    void groupDashboardUsesDocumentedMetricsAndMemberBreakdown() throws Exception {
        Team team = team("metrics");
        long completed = createTask(team.memberToken(), team.groupId(), "완료 업무", "2099-01-01T18:00:00", "NORMAL");
        transition(team.ownerToken(), completed, "ACCEPT", 0, null);
        assign(team.ownerToken(), completed, team.memberId(), 1);
        transition(team.memberToken(), completed, "START", 2, null);
        transition(team.memberToken(), completed, "COMPLETE", 3, null);
        long todo = createTask(team.ownerToken(), team.groupId(), "할 일 업무", null, "LOW");
        transition(team.ownerToken(), todo, "ACCEPT", 0, null);
        long delayed = createTask(team.memberToken(), team.groupId(), "지연 업무", "2020-01-01T09:00:00", "URGENT");

        mvc.perform(get("/api/v1/groups/{groupId}/dashboard", team.groupId())
                        .header("Authorization", bearer(team.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.statuses.requested").value(1))
                .andExpect(jsonPath("$.statuses.todo").value(1))
                .andExpect(jsonPath("$.statuses.completed").value(1))
                .andExpect(jsonPath("$.statuses.delayed").value(1))
                .andExpect(jsonPath("$.workflowProgressPercent").value(42))
                .andExpect(jsonPath("$.periodCreatedCount").value(3))
                .andExpect(jsonPath("$.periodCompletedCount").value(1))
                .andExpect(jsonPath("$.periodCompletionRatePercent").value(33))
                .andExpect(jsonPath("$.completedWithDueDateCount").value(1))
                .andExpect(jsonPath("$.onTimeCompletedCount").value(1))
                .andExpect(jsonPath("$.onTimeRatePercent").value(100))
                .andExpect(jsonPath("$.members[1].memberId").value(team.memberId()))
                .andExpect(jsonPath("$.members[1].assignedCount").value(1))
                .andExpect(jsonPath("$.members[1].completedCount").value(1))
                .andExpect(jsonPath("$.members[1].onTimeRatePercent").value(100))
                .andExpect(jsonPath("$.riskTasks[0].id").value(delayed))
                .andExpect(jsonPath("$.riskTasks[0].delayed").value(true))
                .andExpect(jsonPath("$.statusTasks.length()").value(3));
    }

    @Test
    void leaderOnlyVisibilityIsEnforcedOnServerAndOutsiderGetsNotFound() throws Exception {
        Team team = team("visibility");
        mvc.perform(patch("/api/v1/groups/{groupId}", team.groupId())
                        .header("Authorization", bearer(team.ownerToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dashboardVisibility\":\"LEADER_ONLY\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/groups/{groupId}/dashboard", team.groupId())
                        .header("Authorization", bearer(team.memberToken())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DASHBOARD_FORBIDDEN"));
        mvc.perform(get("/api/v1/groups/{groupId}/reports/me", team.groupId())
                        .param("from", "2026-01-01").param("to", "2027-01-01")
                        .header("Authorization", bearer(team.memberToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(team.memberId()))
                .andExpect(jsonPath("$.tasks").isArray());
        mvc.perform(get("/api/v1/groups/{groupId}/dashboard", team.groupId())
                        .header("Authorization", bearer(team.ownerToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("LEADER_ONLY"));

        Account outsider = account("dashboard_outsider", "dashboard-outsider@example.com");
        mvc.perform(get("/api/v1/groups/{groupId}/dashboard", team.groupId())
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void personalDashboardCombinesAssignedTasksGroupsCalendarAndNotifications() throws Exception {
        Team team = team("personal");
        long personalGroupId = personalGroupId(team.memberToken());
        String todayDue = LocalDate.now().atTime(23, 59, 59).toString();
        long personalTask = createTask(team.memberToken(), personalGroupId, "오늘 개인 업무", todayDue, "HIGH");
        long teamTask = createTask(team.memberToken(), team.groupId(), "진행 팀 업무", null, "URGENT");
        transition(team.ownerToken(), teamTask, "ACCEPT", 0, null);
        assign(team.ownerToken(), teamTask, team.memberId(), 1);
        transition(team.memberToken(), teamTask, "START", 2, null);

        mvc.perform(get("/api/v1/dashboard/me").header("Authorization", bearer(team.memberToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayDueCount").value(1))
                .andExpect(jsonPath("$.inProgressCount").value(1))
                .andExpect(jsonPath("$.delayedCount").value(0))
                .andExpect(jsonPath("$.unreadNotificationCount").value(1))
                .andExpect(jsonPath("$.priorityTasks.length()").value(2))
                .andExpect(jsonPath("$.priorityTasks[0].id").value(personalTask))
                .andExpect(jsonPath("$.groups.length()").value(2))
                .andExpect(jsonPath("$.unreadNotifications[0].type").value("TASK_ASSIGNED"));
    }

    @Test
    void dashboardPeriodMustBeOrderedAndAtMostOneYear() throws Exception {
        Team team = team("range");
        mvc.perform(get("/api/v1/groups/{groupId}/dashboard", team.groupId())
                        .param("from", "2026-09-01").param("to", "2026-08-01")
                        .header("Authorization", bearer(team.ownerToken())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DASHBOARD_RANGE_INVALID"));
        mvc.perform(get("/api/v1/groups/{groupId}/dashboard", team.groupId())
                        .param("from", "2025-01-01").param("to", "2026-01-03")
                        .header("Authorization", bearer(team.ownerToken())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DASHBOARD_RANGE_INVALID"));
    }

    private Team team(String suffix) throws Exception {
        Account owner = account("dashboard_owner_" + suffix, "dashboard-owner-" + suffix + "@example.com");
        var result = mvc.perform(post("/api/v1/groups").header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"대시보드 " + suffix + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        long groupId = number(result, "$.id");
        Account member = account("dashboard_member_" + suffix, "dashboard-member-" + suffix + "@example.com");
        GroupMember membership = members.save(GroupMember.member(groups.findById(groupId).orElseThrow(),
                users.findByUsernameIgnoreCase(member.username()).orElseThrow()));
        return new Team(owner.token(), member.token(), groupId, membership.getId());
    }

    private Account account(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "대시보드 사용자", "password123!", code));
        return new Account(username, sessions.login(new LoginRequest(username, "password123!")).response().accessToken());
    }
    private long personalGroupId(String token) throws Exception {
        var result = mvc.perform(get("/api/v1/groups").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$[0].id")).longValue();
    }
    private long createTask(String token, long groupId, String title, String dueAt, String priority) throws Exception {
        String due = dueAt == null ? "" : ",\"dueAt\":\"" + dueAt + "\"";
        var result = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"priority\":\"" + priority + "\"" + due + "}"))
                .andExpect(status().isCreated()).andReturn();
        return number(result, "$.id");
    }
    private void transition(String token, long taskId, String action, long version, String reason) throws Exception {
        String reasonJson = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
        mvc.perform(post("/api/v1/tasks/{taskId}/transitions", taskId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"" + action + "\",\"expectedVersion\":" + version + reasonJson + "}"))
                .andExpect(status().isOk());
    }
    private void assign(String token, long taskId, long memberId, long version) throws Exception {
        mvc.perform(put("/api/v1/tasks/{taskId}/assignee", taskId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeMemberId\":" + memberId + ",\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk());
    }
    private long number(org.springframework.test.web.servlet.MvcResult result, String path) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
    }
    private String bearer(String token) { return "Bearer " + token; }
    private record Account(String username, String token) {}
    private record Team(String ownerToken, String memberToken, long groupId, long memberId) {}
}
