package com.teamproject.project;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class ProjectIssueApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    @Test
    void buildsStrictThreeLevelFlowAndArchivesSubtree() throws Exception {
        String owner = signupAndLogin("flow_owner", "flow-owner@example.com");
        long groupId = createGroup(owner, "이슈 플로우팀");
        long projectId = createProject(owner, groupId, "사용자 관련 개발");
        long majorId = createNode(owner, projectId, "MAJOR", null, "사용자 기능");
        long middleId = createNode(owner, projectId, "MIDDLE", majorId, "회원가입");

        mvc.perform(post("/api/v1/projects/{projectId}/issues", projectId)
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"ISSUE\",\"parentId\":" + majorId + ",\"title\":\"잘못된 계층\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_ISSUE_HIERARCHY_INVALID"));

        String memberToken = signupAndLogin("flow_member", "flow-member@example.com");
        var memberUser = users.findByUsernameIgnoreCase("flow_member").orElseThrow();
        var member = members.saveAndFlush(GroupMember.member(groups.findById(groupId).orElseThrow(), memberUser));
        var created = mvc.perform(post("/api/v1/projects/{projectId}/issues", projectId)
                        .header("Authorization", "Bearer " + memberToken).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level":"ISSUE","parentId":%d,"title":"가입 폼 구현",
                                 "description":"유효성 검사와 API 연결","assigneeMemberId":%d,"dueDate":"2026-08-30"}
                                """.formatted(middleId, member.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.level").value("ISSUE"))
                .andExpect(jsonPath("$.assigneeNickname").value(memberUser.getNickname()))
                .andExpect(jsonPath("$.canManage").value(true)).andReturn();
        long issueId = number(created.getResponse().getContentAsString(), "$.id");

        var item = mvc.perform(post("/api/v1/project-issues/{issueId}/checklist", issueId)
                        .header("Authorization", "Bearer " + memberToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"이메일 중복 검사 연결\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.completed").value(false)).andReturn();
        long itemId = number(item.getResponse().getContentAsString(), "$.id");
        long itemVersion = number(item.getResponse().getContentAsString(), "$.version");
        mvc.perform(put("/api/v1/project-issue-checklist/{itemId}", itemId)
                        .header("Authorization", "Bearer " + memberToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"expectedVersion\":" + itemVersion + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedByMemberId").value(member.getId()));

        mvc.perform(get("/api/v1/projects/{projectId}/issues", projectId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].checklist[0].content").value("이메일 중복 검사 연결"));

        long majorVersion = nodeVersion(owner, projectId, majorId);
        mvc.perform(delete("/api/v1/project-issues/{issueId}", majorId)
                        .param("expectedVersion", String.valueOf(majorVersion))
                        .header("Authorization", "Bearer " + owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/projects/{projectId}/issues", projectId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/projects/{projectId}/issues", projectId)
                        .param("includeArchived", "true")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].archivedAt").isNotEmpty())
                .andExpect(jsonPath("$[2].checklist[0].content").value("이메일 중복 검사 연결"))
                .andExpect(jsonPath("$[2].canManage").value(false));
    }

    @Test
    void validatesAndProtectsIssueImages() throws Exception {
        String owner = signupAndLogin("image_owner", "image-owner@example.com");
        long groupId = createGroup(owner, "이미지 이슈팀");
        long projectId = createProject(owner, groupId, "상세 이미지 프로젝트");
        long major = createNode(owner, projectId, "MAJOR", null, "화면 개발");
        long middle = createNode(owner, projectId, "MIDDLE", major, "회원가입");
        long issue = createNode(owner, projectId, "ISSUE", middle, "모바일 시안 반영");

        mvc.perform(multipart("/api/v1/project-issues/{issueId}/images", issue)
                        .file(new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes()))
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROJECT_ISSUE_IMAGE_INVALID"));

        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        var uploaded = mvc.perform(multipart("/api/v1/project-issues/{issueId}/images", issue)
                        .file(new MockMultipartFile("file", "signup.png", "image/png", png))
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.contentUrl").isNotEmpty()).andReturn();
        long imageId = number(uploaded.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/project-issue-images/{imageId}/content", imageId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/project-issue-images/{imageId}/content", imageId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
        mvc.perform(delete("/api/v1/project-issues/{issueId}", issue)
                        .param("expectedVersion", String.valueOf(nodeVersion(owner, projectId, issue)))
                        .header("Authorization", "Bearer " + owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/project-issue-images/{imageId}/content", imageId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(content().bytes(png));
        mvc.perform(delete("/api/v1/project-issue-images/{imageId}", imageId)
                        .header("Authorization", "Bearer " + owner)).andExpect(status().isNoContent());
    }

    private long nodeVersion(String token, long projectId, long nodeId) throws Exception {
        var result = mvc.perform(get("/api/v1/projects/{projectId}/issues", projectId)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        java.util.List<Number> versions = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$[?(@.id == " + nodeId + ")].version");
        return versions.getFirst().longValue();
    }
    private long createNode(String token, long projectId, String level, Long parentId, String title) throws Exception {
        String parent = parentId == null ? "" : ",\"parentId\":" + parentId;
        var result = mvc.perform(post("/api/v1/projects/{projectId}/issues", projectId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"" + level + "\",\"title\":\"" + title + "\"" + parent + "}"))
                .andExpect(status().isCreated()).andReturn();
        return number(result.getResponse().getContentAsString(), "$.id");
    }
    private long createProject(String token, long groupId, String name) throws Exception {
        var result = mvc.perform(post("/api/v1/groups/{groupId}/projects", groupId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return number(result.getResponse().getContentAsString(), "$.id");
    }
    private long createGroup(String token, String name) throws Exception {
        var result = mvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return number(result.getResponse().getContentAsString(), "$.id");
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
