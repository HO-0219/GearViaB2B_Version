package com.teamproject.project;

import com.teamproject.B2BGearViaApplication;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = B2BGearViaApplication.class)
@AutoConfigureMockMvc
@Transactional
class ProjectApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    @Test
    void leaderCreatesUpdatesAndArchivesProjectWhileMemberCanRead() throws Exception {
        String ownerToken = signupAndLogin("project_owner", "project-owner@example.com");
        long groupId = createGroup(ownerToken, "제품 개발팀");
        String memberToken = signupAndLogin("project_member", "project-member@example.com");
        var memberUser = users.findByUsernameIgnoreCase("project_member").orElseThrow();
        var member = members.save(GroupMember.member(groups.findById(groupId).orElseThrow(), memberUser));

        var created = mvc.perform(post("/api/v1/groups/{groupId}/projects", groupId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"사용자 인증 개선","description":"회원 기능 이슈 관리",
                                 "leadMemberId":%d,"startDate":"2026-08-13","dueDate":"2026-09-30"}
                                """.formatted(member.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.leadNickname").value(memberUser.getNickname()))
                .andExpect(jsonPath("$.canManage").value(true)).andReturn();
        long projectId = number(created.getResponse().getContentAsString(), "$.id");
        long version = number(created.getResponse().getContentAsString(), "$.version");

        mvc.perform(get("/api/v1/groups/{groupId}/projects", groupId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("사용자 인증 개선"))
                .andExpect(jsonPath("$[0].canManage").value(false))
                .andExpect(jsonPath("$[0].canManageFlow").value(true));

        mvc.perform(put("/api/v1/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"사용자 인증 개선","description":"상세 이슈 연결 준비",
                                 "leadMemberId":%d,"status":"ACTIVE","startDate":"2026-08-13",
                                 "dueDate":"2026-09-30","expectedVersion":%d}
                                """.formatted(member.getId(), version)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.description").value("상세 이슈 연결 준비"));

        mvc.perform(delete("/api/v1/projects/{projectId}", projectId)
                        .param("expectedVersion", String.valueOf(version + 1))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void projectMutationsRequireLeaderAndValidateDatesAndVersions() throws Exception {
        String ownerToken = signupAndLogin("project_guard_owner", "project-guard-owner@example.com");
        long groupId = createGroup(ownerToken, "권한 프로젝트팀");
        String memberToken = signupAndLogin("project_guard_member", "project-guard-member@example.com");
        var memberUser = users.findByUsernameIgnoreCase("project_guard_member").orElseThrow();
        members.save(GroupMember.member(groups.findById(groupId).orElseThrow(), memberUser));

        mvc.perform(post("/api/v1/groups/{groupId}/projects", groupId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"권한 없음\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));

        mvc.perform(post("/api/v1/groups/{groupId}/projects", groupId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"잘못된 기간\",\"startDate\":\"2026-09-01\",\"dueDate\":\"2026-08-01\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROJECT_DATE_INVALID"));

        var created = mvc.perform(post("/api/v1/groups/{groupId}/projects", groupId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"버전 프로젝트\"}"))
                .andExpect(status().isCreated()).andReturn();
        long projectId = number(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/v1/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"충돌\",\"status\":\"ACTIVE\",\"expectedVersion\":99}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_VERSION_CONFLICT"));
    }

    @Test
    void featurePolicyChangesWithGroupPlan() throws Exception {
        String token = signupAndLogin("feature_owner", "feature-owner@example.com");
        long groupId = createGroup(token, "기능 정책팀");
        mvc.perform(get("/api/v1/groups/{groupId}/features", groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipPlan").value("FREE"))
                .andExpect(jsonPath("$.multipleChatChannels").value(false))
                .andExpect(jsonPath("$.chatChannelLimit").value(1))
                .andExpect(jsonPath("$.messageRetentionDays").value(10));

        mvc.perform(put("/api/v1/groups/{groupId}/membership/test-plan", groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PAID\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/groups/{groupId}/features", groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipPlan").value("PAID"))
                .andExpect(jsonPath("$.multipleChatChannels").value(true))
                .andExpect(jsonPath("$.chatChannelLimit").value(50))
                .andExpect(jsonPath("$.messageRetentionDays").value(365));
    }

    private long createGroup(String token, String name) throws Exception {
        var created = mvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return number(created.getResponse().getContentAsString(), "$.id");
    }

    private long number(String json, String path) {
        return ((Number) com.jayway.jsonpath.JsonPath.read(json, path)).longValue();
    }

    private String signupAndLogin(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "프로젝트 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }
}
