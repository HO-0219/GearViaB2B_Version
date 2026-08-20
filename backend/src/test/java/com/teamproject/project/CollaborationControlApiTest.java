package com.teamproject.project;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import java.util.Base64;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=B2BGearViaApplication.class) @AutoConfigureMockMvc @Transactional
class CollaborationControlApiTest {
    @Autowired MockMvc mvc; @Autowired SignupService signup; @Autowired SessionService sessions;
    @Autowired OneTimeTokenService codes; @Autowired UserRepository users; @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    @Test void assigneeChangeNeedsLeaderApproval() throws Exception {
        String leader=signupAndLogin("change_leader","change-leader@example.com");
        long groupId=createGroup(leader,"담당자 승인 팀");
        String first=signupAndLogin("change_first","change-first@example.com");
        String second=signupAndLogin("change_second","change-second@example.com");
        GroupMember firstMember=join(groupId,"change_first"), secondMember=join(groupId,"change_second");
        var task=mvc.perform(post("/api/v1/groups/{id}/tasks",groupId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"담당자 교체 업무\"}"))
                .andExpect(status().isCreated()).andReturn();
        long taskId=number(task,"$.id"),version=number(task,"$.version");
        version=number(mvc.perform(post("/api/v1/tasks/{id}/transitions",taskId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\",\"expectedVersion\":"+version+"}"))
                .andExpect(status().isOk()).andReturn(),"$.version");
        var assigned=mvc.perform(put("/api/v1/tasks/{id}/assignee",taskId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeMemberId\":"+firstMember.getId()+",\"expectedVersion\":"+version+"}"))
                .andExpect(status().isOk()).andReturn(); version=number(assigned,"$.version");
        mvc.perform(put("/api/v1/tasks/{id}/assignee",taskId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeMemberId\":"+secondMember.getId()+",\"expectedVersion\":"+version+"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSIGNEE_CHANGE_APPROVAL_REQUIRED"));
        var requested=mvc.perform(post("/api/v1/tasks/{id}/assignee-change-requests",taskId).header("Authorization","Bearer "+first)
                .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeMemberId\":"+secondMember.getId()+",\"reason\":\"일정 조정\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        long requestId=number(requested,"$.id"),requestVersion=number(requested,"$.version");
        mvc.perform(post("/api/v1/task-assignee-change-requests/{id}/decision",requestId).header("Authorization","Bearer "+second)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"expectedVersion\":"+requestVersion+"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/task-assignee-change-requests/{id}/decision",requestId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"expectedVersion\":"+requestVersion+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/v1/tasks/{id}",taskId).header("Authorization","Bearer "+second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assigneeMemberId").value(secondMember.getId()));
    }

    @Test void emergencyIssueTracksAudienceAndResolution() throws Exception {
        String leader=signupAndLogin("urgent_leader","urgent-leader@example.com"); long groupId=createGroup(leader,"긴급 대응 팀");
        var project=mvc.perform(post("/api/v1/groups/{id}/projects",groupId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"출시 프로젝트\"}"))
                .andExpect(status().isCreated()).andReturn(); long projectId=number(project,"$.id");
        var created=mvc.perform(post("/api/v1/groups/{id}/emergency-issues",groupId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":"+projectId+",\"title\":\"배포 중단\",\"description\":\"로그 확인 필요\",\"audience\":\"WHOLE_TEAM\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.audience").value("WHOLE_TEAM")).andReturn();
        long issueId=number(created,"$.id"),version=number(created,"$.version");
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        mvc.perform(multipart("/api/v1/emergency-issues/{id}/image",issueId)
                .file(new MockMultipartFile("file","incident.png","image/png",png))
                .header("Authorization","Bearer "+leader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.containsString("/uploads/emergency-issues/")));
        version++;
        mvc.perform(get("/api/v1/groups/{id}/emergency-issues",groupId).header("Authorization","Bearer "+leader))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].projectName").value("출시 프로젝트"));
        mvc.perform(patch("/api/v1/emergency-issues/{id}/status",issueId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\",\"expectedVersion\":"+version+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());
    }

    @Test void topicHasOwnerAndActiveStandaloneTaskCanJoinHierarchy() throws Exception {
        String leader=signupAndLogin("hierarchy_leader","hierarchy-leader@example.com");
        long groupId=createGroup(leader,"계층 협업 팀");
        String memberToken=signupAndLogin("hierarchy_member","hierarchy-member@example.com");
        GroupMember member=join(groupId,"hierarchy_member");
        var project=mvc.perform(post("/api/v1/groups/{id}/projects",groupId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"계층 프로젝트\",\"leadMemberId\":"+member.getId()+"}"))
                .andExpect(status().isCreated()).andReturn(); long projectId=number(project,"$.id");
        var topic=mvc.perform(post("/api/v1/projects/{id}/issues",projectId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"MAJOR\",\"title\":\"핵심 주제\",\"assigneeMemberId\":"+member.getId()+",\"dueDate\":\"2026-09-01\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.assigneeMemberId").value(member.getId()))
                .andExpect(jsonPath("$.assigneeNickname").isNotEmpty()).andReturn(); long topicId=number(topic,"$.id");
        mvc.perform(get("/api/v1/projects/{id}/issues",projectId).header("Authorization","Bearer "+memberToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].canManage").value(true));
        var task=mvc.perform(post("/api/v1/groups/{id}/tasks",groupId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"처음엔 독립 업무\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.projectId").doesNotExist()).andReturn();
        long taskId=number(task,"$.id"),version=number(task,"$.version");
        var accepted=mvc.perform(post("/api/v1/tasks/{id}/transitions",taskId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\",\"expectedVersion\":"+version+"}"))
                .andExpect(status().isOk()).andReturn(); version=number(accepted,"$.version");
        var assigned=mvc.perform(put("/api/v1/tasks/{id}/assignee",taskId).header("Authorization","Bearer "+leader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeMemberId\":"+member.getId()+",\"expectedVersion\":"+version+"}"))
                .andExpect(status().isOk()).andReturn(); version=number(assigned,"$.version");
        mvc.perform(patch("/api/v1/tasks/{id}/project-link",taskId).header("Authorization","Bearer "+memberToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":"+projectId+",\"projectTopicId\":"+topicId+",\"expectedVersion\":"+version+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectName").value("계층 프로젝트"))
                .andExpect(jsonPath("$.projectTopicTitle").value("핵심 주제"));
    }

    private GroupMember join(long groupId,String username){return members.save(GroupMember.member(groups.findById(groupId).orElseThrow(),users.findByUsernameIgnoreCase(username).orElseThrow()));}
    private long createGroup(String token,String name)throws Exception{return number(mvc.perform(post("/api/v1/groups").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\""+name+"\"}")).andExpect(status().isCreated()).andReturn(),"$.id");}
    private long number(org.springframework.test.web.servlet.MvcResult result,String path)throws Exception{return ((Number)JsonPath.read(result.getResponse().getContentAsString(),path)).longValue();}
    private String signupAndLogin(String username,String email){signup.signup(new SignupRequest(username,email,"협업 사용자","password123!",codes.issueCode(email)));return sessions.login(new LoginRequest(username,"password123!")).response().accessToken();}
}
