package com.teamproject.qa;

import com.jayway.jsonpath.JsonPath;
import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.authentication.infrastructure.crypto.HashService;
import com.teamproject.group.domain.GroupInvitation;
import com.teamproject.group.domain.GroupInvitationRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class LocalAlphaCoreFlowApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired GroupInvitationRepository invitations;
    @Autowired HashService hashes;

    @Test
    void memberJoinsTeamAndCompletesWorkAcrossCollaborationFeatures() throws Exception {
        Account owner = account("alpha_flow_owner", "alpha-flow-owner@example.com", "알파 팀장");
        Account member = account("alpha_flow_member", "alpha-flow-member@example.com", "알파 팀원");

        var createdGroup = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"로컬 알파 팀\",\"timezone\":\"Asia/Seoul\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("LEADER"))
                .andReturn();
        long groupId = number(createdGroup, "$.id");
        long ownerMemberId = number(createdGroup, "$.memberId");

        String invitationToken = "local-alpha-core-flow-invitation";
        GroupMember inviter = members.findByGroupIdAndUserIdAndStatus(
                groupId, owner.user().getId(), GroupMember.Status.ACTIVE).orElseThrow();
        invitations.save(new GroupInvitation(groups.findById(groupId).orElseThrow(), member.user().getEmail(),
                inviter, hashes.sha256(invitationToken), LocalDateTime.now().plusHours(1)));

        var accepted = mvc.perform(post("/api/v1/group-invitations/{token}/accept", invitationToken)
                        .header("Authorization", bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.nickname").value("알파 팀원"))
                .andReturn();
        long memberId = number(accepted, "$.id");

        LocalDate dueDate = LocalDate.now().plusDays(1);
        var createdTask = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"알파 시연 업무\",\"description\":\"종단 흐름 검증\","
                                + "\"priority\":\"HIGH\",\"dueAt\":\"" + dueDate + "T18:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.requesterMemberId").value(memberId))
                .andReturn();
        long taskId = number(createdTask, "$.id");

        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].type").value("TASK_REQUESTED"))
                .andExpect(jsonPath("$.items[0].taskId").value(taskId));

        transition(owner.token(), taskId, "ACCEPT", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/tasks/{taskId}/assignee", taskId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeMemberId\":" + memberId + ",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeMemberId").value(memberId))
                .andExpect(jsonPath("$.version").value(2));
        transition(member.token(), taskId, "START", 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(3));

        var createdItem = mvc.perform(post("/api/v1/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"로컬 결과 확인\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.completed").value(false))
                .andReturn();
        long checklistItemId = number(createdItem, "$.id");
        mvc.perform(patch("/api/v1/checklist-items/{itemId}", checklistItemId)
                        .header("Authorization", bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedByMemberId").value(memberId));
        mvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.progressPercent").value(100));

        mvc.perform(post("/api/v1/tasks/{taskId}/comments", taskId)
                        .header("Authorization", bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"완료 전 확인 부탁드립니다.\",\"mentionedMemberIds\":["
                                + ownerMemberId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions[0].memberId").value(ownerMemberId));

        transition(member.token(), taskId, "COMPLETE", 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.version").value(4));

        mvc.perform(get("/api/v1/tasks/{taskId}/histories", taskId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[3].toStatus").value("COMPLETED"));
        mvc.perform(get("/api/v1/calendars/events")
                        .param("groupId", String.valueOf(groupId))
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().plusDays(3).toString())
                        .header("Authorization", bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].source").value("TASK_DEADLINE"))
                .andExpect(jsonPath("$.items[0].sourceTaskId").value(taskId));
        mvc.perform(get("/api/v1/groups/{groupId}/dashboard", groupId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.statuses.completed").value(1))
                .andExpect(jsonPath("$.periodCompletionRatePercent").value(100))
                .andExpect(jsonPath("$.members[1].memberId").value(memberId))
                .andExpect(jsonPath("$.members[1].completedCount").value(1));
        mvc.perform(get("/api/v1/dashboard/me").header("Authorization", bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityTasks.length()").value(0))
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].groupId").value(groupId))
                .andExpect(jsonPath("$.groups[0].completedCount").value(1))
                .andExpect(jsonPath("$.unreadNotificationCount").value(1))
                .andExpect(jsonPath("$.unreadNotifications[0].type").value("TASK_ASSIGNED"));
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.items[0].type").value("TASK_STATUS_CHANGED"))
                .andExpect(jsonPath("$.items[1].type").value("COMMENT_MENTIONED"));
    }

    private org.springframework.test.web.servlet.ResultActions transition(
            String token, long taskId, String action, long version) throws Exception {
        return mvc.perform(post("/api/v1/tasks/{taskId}/transitions", taskId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\",\"expectedVersion\":" + version + "}"));
    }

    private Account account(String username, String email, String name) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, name, "password123!", code));
        User user = users.findByUsernameIgnoreCase(username).orElseThrow();
        String token = sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
        return new Account(user, token);
    }

    private long number(org.springframework.test.web.servlet.MvcResult result, String path) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
    }

    private String bearer(String token) { return "Bearer " + token; }
    private record Account(User user, String token) {}
}
