package com.teamproject.project;

import com.teamproject.B2BGearViaApplication;
import com.teamproject.authentication.application.*;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = B2BGearViaApplication.class, properties = {
        "app.organization.storage.total-bytes=12",
        "app.organization.storage.attachment-limit-bytes=1000"
})
@AutoConfigureMockMvc
@Transactional
class ProjectDocumentApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService tokens;

    @Test
    void storesFilesAndLinksAtProjectHierarchyLocations() throws Exception {
        String owner = signupAndLogin("document_owner", "document-owner@example.com");
        long groupId = createGroup(owner, "파일 시스템팀");
        long projectId = createProject(owner, groupId, "회원 시스템");
        long majorId = createNode(owner, projectId, "MAJOR", null, "사용자 관련 개발");

        var uploaded = mvc.perform(multipart("/api/v1/projects/{projectId}/documents/files", projectId)
                        .file(new MockMultipartFile("file", "spec.txt", "text/plain", "spec".getBytes()))
                        .param("issueNodeId", String.valueOf(majorId)).param("title", "회원 기능 명세")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.issueNodeId").value(majorId))
                .andExpect(jsonPath("$.sizeBytes").value(4)).andReturn();
        long documentId = number(uploaded.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/v1/projects/{projectId}/documents/links", projectId)
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"디자인 문서\",\"url\":\"https://docs.example.com/signup\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("LINK"))
                .andExpect(jsonPath("$.issueNodeId").doesNotExist());
        mvc.perform(get("/api/v1/projects/{projectId}/documents", projectId)
                        .param("issueNodeId", String.valueOf(majorId))
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usedBytes").value(4))
                .andExpect(jsonPath("$.limitBytes").value(12))
                .andExpect(jsonPath("$.rootDocuments[0].title").value("디자인 문서"))
                .andExpect(jsonPath("$.nodeDocuments[0].title").value("회원 기능 명세"));
        mvc.perform(get("/api/v1/project-documents/{documentId}/download", documentId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/project-documents/{documentId}/download", documentId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(content().bytes("spec".getBytes()));
        long majorVersion = number(mvc.perform(get("/api/v1/projects/{projectId}/issues", projectId)
                        .header("Authorization", "Bearer " + owner)).andReturn()
                .getResponse().getContentAsString(), "$[0].version");
        mvc.perform(delete("/api/v1/project-issues/{issueId}", majorId)
                        .param("expectedVersion", String.valueOf(majorVersion))
                        .header("Authorization", "Bearer " + owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/projects/{projectId}/documents", projectId)
                        .param("issueNodeId", String.valueOf(majorId))
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nodeDocuments[0].id").value(documentId));
        mvc.perform(multipart("/api/v1/projects/{projectId}/documents/files", projectId)
                        .file(new MockMultipartFile("file", "blocked.txt", "text/plain", "x".getBytes()))
                        .param("issueNodeId", String.valueOf(majorId))
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/project-documents/{documentId}", documentId)
                        .header("Authorization", "Bearer " + owner)).andExpect(status().isNoContent());
    }

    @Test
    void sharesQuotaWithExistingGroupResourcesAndRejectsWrongLocation() throws Exception {
        String owner = signupAndLogin("quota_owner", "quota-owner@example.com");
        long groupId = createGroup(owner, "저장공간팀");
        long projectId = createProject(owner, groupId, "저장공간 프로젝트");
        long otherProjectId = createProject(owner, groupId, "다른 프로젝트");
        long otherMajor = createNode(owner, otherProjectId, "MAJOR", null, "다른 위치");

        mvc.perform(multipart("/api/v1/groups/{groupId}/resources/files", groupId)
                        .file(new MockMultipartFile("file", "shared.txt", "text/plain", "12345678".getBytes()))
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated());
        mvc.perform(multipart("/api/v1/projects/{projectId}/documents/files", projectId)
                        .file(new MockMultipartFile("file", "overflow.txt", "text/plain", "12345".getBytes()))
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_STORAGE_LIMIT_EXCEEDED"));
        mvc.perform(multipart("/api/v1/projects/{projectId}/documents/files", projectId)
                        .file(new MockMultipartFile("file", "wrong.txt", "text/plain", "1".getBytes()))
                        .param("issueNodeId", String.valueOf(otherMajor))
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_DOCUMENT_LOCATION_NOT_FOUND"));
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
    private long number(String json, String path) { return ((Number)
            com.jayway.jsonpath.JsonPath.read(json, path)).longValue(); }
    private String signupAndLogin(String username, String email) {
        String code = tokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "문서 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }
}
