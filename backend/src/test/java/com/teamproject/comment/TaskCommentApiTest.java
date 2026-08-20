package com.teamproject.comment;

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
import com.teamproject.comment.domain.CommentRevisionRepository;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class TaskCommentApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired CommentRevisionRepository revisions;

    @Test
    void activeMembersCreateAndAuthorUpdatesAndSoftDeletes() throws Exception {
        Fixture fixture = fixture("manage");
        long commentId = createComment(fixture.memberToken(), fixture.taskId(), "첫 댓글");

        mvc.perform(get("/api/v1/tasks/{taskId}/comments", fixture.taskId())
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(commentId))
                .andExpect(jsonPath("$[0].authorMemberId").value(fixture.memberId()))
                .andExpect(jsonPath("$[0].authorNickname").value("댓글 사용자"))
                .andExpect(jsonPath("$[0].content").value("첫 댓글"))
                .andExpect(jsonPath("$[0].deleted").value(false))
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());

        mvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정 댓글\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정 댓글"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1));
        org.assertj.core.api.Assertions.assertThat(revisions.count()).isEqualTo(1);

        mvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .param("expectedVersion", "1")
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tasks/{taskId}/comments", fixture.taskId())
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("삭제된 댓글입니다."))
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].deletedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].version").value(2));
    }

    @Test
    void leaderCannotChangeAnotherAuthorsCommentAndOutsiderCannotRead() throws Exception {
        Fixture fixture = fixture("access");
        long commentId = createComment(fixture.memberToken(), fixture.taskId(), "팀원 댓글");

        mvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"팀장 수정\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMENT_AUTHOR_REQUIRED"));
        mvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .param("expectedVersion", "0")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isForbidden());

        String outsider = signupAndLogin("comment_outsider", "comment-outsider@example.com");
        mvc.perform(get("/api/v1/tasks/{taskId}/comments", fixture.taskId())
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        mvc.perform(post("/api/v1/tasks/{taskId}/comments", fixture.taskId())
                        .header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"외부 댓글\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankStaleAndDeletedCommentChangesAreRejected() throws Exception {
        Fixture fixture = fixture("conflict");
        mvc.perform(post("/api/v1/tasks/{taskId}/comments", fixture.taskId())
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());
        long commentId = createComment(fixture.memberToken(), fixture.taskId(), "버전 댓글");

        mvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"오래된 수정\",\"expectedVersion\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_VERSION_CONFLICT"));
        mvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .param("expectedVersion", "0")
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"삭제 후 수정\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_ALREADY_DELETED"));
    }

    @Test
    void terminalTaskStillAcceptsComments() throws Exception {
        Fixture fixture = fixture("terminal");
        transition(fixture.ownerToken(), fixture.taskId(), "ACCEPT", 0);
        mvc.perform(put("/api/v1/tasks/{taskId}/assignee", fixture.taskId())
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeMemberId\":" + fixture.memberId() + ",\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        transition(fixture.memberToken(), fixture.taskId(), "START", 2);
        transition(fixture.memberToken(), fixture.taskId(), "COMPLETE", 3);

        long commentId = createComment(fixture.memberToken(), fixture.taskId(), "완료 후 회고");
        mvc.perform(get("/api/v1/tasks/{taskId}/comments", fixture.taskId())
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("완료 후 회고"));
        mvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"완료 후 수정\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_RECORD_LOCKED"));
        mvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .param("expectedVersion", "0")
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_RECORD_LOCKED"));
        mvc.perform(post("/api/v1/tasks/{taskId}/transitions", fixture.taskId())
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REOPEN\",\"reason\":\"후속 작업\",\"expectedVersion\":4}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/tasks/{taskId}/comments", fixture.taskId())
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recordLocked").value(true));
        mvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"재개 후 과거 수정\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_RECORD_LOCKED"));
    }

    @Test
    void mentionsAreDeduplicatedReplacedAndHiddenAfterDelete() throws Exception {
        Fixture fixture = fixture("mentions");
        var created = mvc.perform(post("/api/v1/tasks/{taskId}/comments", fixture.taskId())
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"두 명 확인\",\"mentionedMemberIds\":["
                                + fixture.ownerMemberId() + "," + fixture.memberId() + ","
                                + fixture.memberId() + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions.length()").value(2))
                .andExpect(jsonPath("$.mentions[0].memberId").value(fixture.ownerMemberId()))
                .andExpect(jsonPath("$.mentions[1].memberId").value(fixture.memberId()))
                .andReturn();
        long commentId = ((Number) JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"팀장만 확인\",\"mentionedMemberIds\":["
                                + fixture.ownerMemberId() + "],\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentions.length()").value(1))
                .andExpect(jsonPath("$.mentions[0].memberId").value(fixture.ownerMemberId()));
        mvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .param("expectedVersion", "1")
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tasks/{taskId}/comments", fixture.taskId())
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].mentions.length()").value(0));
    }

    @Test
    void inactiveAndOtherGroupMembersCannotBeMentioned() throws Exception {
        Fixture fixture = fixture("mention_scope");
        String inactiveUsername = "comment_inactive";
        signupAndLogin(inactiveUsername, "comment-inactive@example.com");
        GroupMember inactive = addMember(fixture.groupId(), inactiveUsername);
        inactive.leave();
        members.save(inactive);

        mvc.perform(post("/api/v1/tasks/{taskId}/comments", fixture.taskId())
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"탈퇴 멤버\",\"mentionedMemberIds\":["
                                + inactive.getId() + "]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_NOT_FOUND"));

        String otherOwnerToken = signupAndLogin("comment_other_owner", "comment-other-owner@example.com");
        Team otherTeam = createTeam(otherOwnerToken, "다른 댓글 팀");
        mvc.perform(post("/api/v1/tasks/{taskId}/comments", fixture.taskId())
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"다른 그룹\",\"mentionedMemberIds\":["
                                + otherTeam.ownerMemberId() + "]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_NOT_FOUND"));
    }

    private Fixture fixture(String suffix) throws Exception {
        String ownerUsername = "comment_owner_" + suffix;
        String ownerToken = signupAndLogin(ownerUsername, ownerUsername + "@example.com");
        Team team = createTeam(ownerToken, "댓글 " + suffix);
        long groupId = team.groupId();
        String memberUsername = "comment_member_" + suffix;
        String memberToken = signupAndLogin(memberUsername, memberUsername + "@example.com");
        GroupMember member = addMember(groupId, memberUsername);
        long taskId = createTask(ownerToken, groupId, "댓글 업무 " + suffix);
        return new Fixture(ownerToken, memberToken, groupId, team.ownerMemberId(), member.getId(), taskId);
    }

    private long createComment(String token, long taskId, String content) throws Exception {
        var result = mvc.perform(post("/api/v1/tasks/{taskId}/comments", taskId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void transition(String token, long taskId, String action, long version) throws Exception {
        mvc.perform(post("/api/v1/tasks/{taskId}/transitions", taskId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"" + action + "\",\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk());
    }

    private long createTask(String token, long groupId, String title) throws Exception {
        var result = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Team createTeam(String token, String name) throws Exception {
        var result = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        String body = result.getResponse().getContentAsString();
        return new Team(((Number) JsonPath.read(body, "$.id")).longValue(),
                ((Number) JsonPath.read(body, "$.memberId")).longValue());
    }

    private GroupMember addMember(long groupId, String username) {
        return members.save(GroupMember.member(groups.findById(groupId).orElseThrow(),
                users.findByUsernameIgnoreCase(username).orElseThrow()));
    }

    private String signupAndLogin(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "댓글 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }

    private String bearer(String token) { return "Bearer " + token; }
    private record Fixture(String ownerToken, String memberToken, long groupId,
                           long ownerMemberId, long memberId, long taskId) {}
    private record Team(long groupId, long ownerMemberId) {}
}
