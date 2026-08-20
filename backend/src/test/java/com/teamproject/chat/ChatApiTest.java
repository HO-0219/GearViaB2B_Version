package com.teamproject.chat;

import com.teamproject.B2BGearViaApplication;
import com.teamproject.authentication.application.*;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.chat.application.ChatSocketTicketService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.*;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = B2BGearViaApplication.class)
@AutoConfigureMockMvc
@Transactional
class ChatApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired ChatSocketTicketService socketTickets;
    @Autowired JdbcTemplate jdbc;

    @Test
    void companyPolicyUsesConfiguredChannelsAndHistory() throws Exception {
        String owner = signupAndLogin("chat_policy_owner", "chat-policy-owner@example.com");
        long groupId = createGroup(owner, "회사 정책 채팅팀");
        var listed = mvc.perform(get("/api/v1/groups/{groupId}/chat/channels", groupId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("GENERAL"))
                .andExpect(jsonPath("$[0].retentionDays").value(365)).andReturn();
        long channelId = number(listed.getResponse().getContentAsString(), "$[0].id");
        mvc.perform(post("/api/v1/groups/{groupId}/chat/channels", groupId)
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"추가 채팅\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("TOPIC"));
        var message = mvc.perform(post("/api/v1/chat/channels/{channelId}/messages", channelId)
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"첫 번째 메시지\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("첫 번째 메시지")).andReturn();
        long messageId = number(message.getResponse().getContentAsString(), "$.id");
        jdbc.update("UPDATE chat_messages SET created_at = ? WHERE id = ?", LocalDateTime.now().minusDays(366), messageId);
        mvc.perform(get("/api/v1/chat/channels/{channelId}/messages", channelId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.retentionDays").value(365));
    }

    @Test
    void teamCreatesMajorTopicAndMembersExchangeAttachments() throws Exception {
        String owner = signupAndLogin("chat_team_owner", "chat-team-owner@example.com");
        long groupId = createGroup(owner, "팀 채팅");
        long projectId = createProject(owner, groupId, "사용자 프로젝트");
        long majorId = createNode(owner, projectId, "사용자 관련 개발");
        var created = mvc.perform(post("/api/v1/groups/{groupId}/chat/channels", groupId)
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"사용자 기능 논의\",\"projectId\":" + projectId
                                + ",\"issueNodeId\":" + majorId + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("TOPIC"))
                .andExpect(jsonPath("$.issueNodeTitle").value("사용자 관련 개발"))
                .andExpect(jsonPath("$.retentionDays").value(365)).andReturn();
        long channelId = number(created.getResponse().getContentAsString(), "$.id");

        String memberToken = signupAndLogin("chat_team_member", "chat-team-member@example.com");
        var memberUser = users.findByUsernameIgnoreCase("chat_team_member").orElseThrow();
        members.saveAndFlush(GroupMember.member(groups.findById(groupId).orElseThrow(), memberUser));
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        var uploaded = mvc.perform(multipart("/api/v1/chat/channels/{channelId}/attachments", channelId)
                        .file(new MockMultipartFile("file", "screen.png", "image/png", png))
                        .param("caption", "회원가입 화면")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("IMAGE"))
                .andExpect(jsonPath("$.senderNickname").value(memberUser.getNickname())).andReturn();
        long messageId = number(uploaded.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/chat/messages/{messageId}/content", messageId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/chat/messages/{messageId}/content", messageId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("CHAT_MESSAGE"))
                .andExpect(jsonPath("$.items[0].actorNickname").value(memberUser.getNickname()))
                .andExpect(jsonPath("$.items[0].targetUrl")
                        .value("/groups/" + groupId + "/chat?channel=" + channelId));
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void socketTicketIsOpaqueShortLivedAndSingleUse() throws Exception {
        String token = signupAndLogin("chat_ticket_user", "chat-ticket@example.com");
        var issued = mvc.perform(post("/api/v1/chat/socket-tickets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.expiresInSeconds").value(60)).andReturn();
        String ticket = com.jayway.jsonpath.JsonPath.read(issued.getResponse().getContentAsString(), "$.ticket");
        socketTickets.consume(ticket);
        assertThatThrownBy(() -> socketTickets.consume(ticket)).isInstanceOf(ApplicationException.class)
                .hasMessageContaining("만료");
    }

    private long createNode(String token, long projectId, String title) throws Exception {
        var result = mvc.perform(post("/api/v1/projects/{projectId}/issues", projectId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"MAJOR\",\"title\":\"" + title + "\"}"))
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
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "채팅 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }
}
