package com.teamproject.task;

import com.jayway.jsonpath.JsonPath;
import com.teamproject.B2BGearViaApplication;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskStatusHistoryRepository;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = B2BGearViaApplication.class)
@AutoConfigureMockMvc
@Transactional
class TaskApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskStatusHistoryRepository histories;

    @Test
    void personalTaskStartsAsTodoAssignedToRequesterAndRecordsInitialHistory() throws Exception {
        String token = signupAndLogin("task_personal", "task-personal@example.com");
        long userId = users.findByUsernameIgnoreCase("task_personal").orElseThrow().getId();
        GroupMember personalMember = members.findAllByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(
                        userId, GroupMember.Status.ACTIVE).stream()
                .filter(member -> member.getGroup().getType() == Group.Type.PERSONAL)
                .findFirst().orElseThrow();

        var created = mvc.perform(post("/api/v1/groups/{groupId}/tasks", personalMember.getGroup().getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"오늘 할 일\",\"description\":\" 로컬 테스트 \",\"priority\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.requesterMemberId").value(personalMember.getId()))
                .andExpect(jsonPath("$.assigneeMemberId").value(personalMember.getId()))
                .andExpect(jsonPath("$.description").value("로컬 테스트"))
                .andReturn();

        long taskId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        var initialHistory = histories.findAllByTaskIdOrderByCreatedAtAsc(taskId);
        assertThat(initialHistory).hasSize(1);
        assertThat(initialHistory.getFirst().getFromStatus()).isNull();
        assertThat(initialHistory.getFirst().getToStatus()).isEqualTo(Task.Status.TODO);
        assertThat(initialHistory.getFirst().getChangedBy().getId()).isEqualTo(personalMember.getId());
    }

    @Test
    void teamTaskStartsRequestedAndCanBeListedAndReadWithDelayDerived() throws Exception {
        String token = signupAndLogin("task_owner", "task-owner@example.com");
        long groupId = createTeam(token, "업무 테스트 팀");

        var created = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"검토 요청\",\"priority\":\"urgent\",\"dueAt\":\"2020-01-01T09:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.assigneeMemberId").doesNotExist())
                .andExpect(jsonPath("$.delayed").value(true))
                .andReturn();
        long taskId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(get("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(taskId))
                .andExpect(jsonPath("$[0].title").value("검토 요청"));
        mvc.perform(get("/api/v1/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(groupId))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void taskValidationAndMembershipAreEnforced() throws Exception {
        String ownerToken = signupAndLogin("task_access_owner", "task-access-owner@example.com");
        long groupId = createTeam(ownerToken, "업무 접근 팀");
        var created = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"접근 제한 업무\"}"))
                .andExpect(status().isCreated()).andReturn();
        long taskId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String outsiderToken = signupAndLogin("task_outsider", "task-outsider@example.com");

        mvc.perform(get("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        mvc.perform(get("/api/v1/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"침입 업무\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"잘못된 우선순위\",\"priority\":\"MAX\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TASK_PRIORITY_INVALID"));
    }

    @Test
    void projectTopicTaskCanBeCompletedCorrectedAndSoftDeleted() throws Exception {
        String token = signupAndLogin("task_project_owner", "task-project-owner@example.com");
        long userId = users.findByUsernameIgnoreCase("task_project_owner").orElseThrow().getId();
        long groupId = createTeam(token, "연결 업무 팀");
        long memberId = members.findAllByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(
                        userId, GroupMember.Status.ACTIVE).stream()
                .filter(member -> member.getGroup().getId().equals(groupId)).findFirst().orElseThrow().getId();

        var project = mvc.perform(post("/api/v1/groups/{groupId}/projects", groupId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"웹 개편\"}"))
                .andExpect(status().isCreated()).andReturn();
        long projectId = number(project, "$.id");
        var topic = mvc.perform(post("/api/v1/projects/{projectId}/issues", projectId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"MAJOR\",\"title\":\"대시보드\"}"))
                .andExpect(status().isCreated()).andReturn();
        long topicId = number(topic, "$.id");

        var created = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"프로젝트 현황 연결","projectId":%d,"projectTopicId":%d}
                                """.formatted(projectId, topicId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.projectName").value("웹 개편"))
                .andExpect(jsonPath("$.projectTopicId").value(topicId))
                .andExpect(jsonPath("$.projectTopicTitle").value("대시보드"))
                .andReturn();
        long taskId = number(created, "$.id");
        long version = number(created, "$.version");

        version = transition(token, taskId, "ACCEPT", version);
        var assigned = mvc.perform(put("/api/v1/tasks/{taskId}/assignee", taskId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeMemberId\":" + memberId + ",\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk()).andReturn();
        version = number(assigned, "$.version");
        version = transition(token, taskId, "START", version);
        version = transition(token, taskId, "COMPLETE", version);

        var corrected = mvc.perform(patch("/api/v1/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"프로젝트 현황 연결 완료\",\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.title").value("프로젝트 현황 연결 완료"))
                .andExpect(jsonPath("$.projectTopicId").value(topicId)).andReturn();
        version = number(corrected, "$.version");

        mvc.perform(delete("/api/v1/tasks/{taskId}", taskId)
                        .param("expectedVersion", String.valueOf(version))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private long transition(String token, long taskId, String action, long version) throws Exception {
        var result = mvc.perform(post("/api/v1/tasks/{taskId}/transitions", taskId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"" + action + "\",\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk()).andReturn();
        return number(result, "$.version");
    }

    private long number(org.springframework.test.web.servlet.MvcResult result, String path) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
    }

    private long createTeam(String token, String name) throws Exception {
        var created = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String signupAndLogin(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "업무 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }
}
