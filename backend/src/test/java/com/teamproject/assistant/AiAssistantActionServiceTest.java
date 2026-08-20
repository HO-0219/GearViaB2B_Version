package com.teamproject.assistant;

import com.teamproject.TeamProjectApplication;
import com.teamproject.assistant.application.AiAssistantActionService;
import com.teamproject.assistant.application.AiAssistantMessageStore;
import com.teamproject.assistant.domain.AiAssistantAction;
import com.teamproject.assistant.domain.AiAssistantActionRepository;
import com.teamproject.assistant.domain.AiAssistantMessage;
import com.teamproject.comment.domain.TaskCommentRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.notification.domain.Notification;
import com.teamproject.notification.domain.NotificationRepository;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.task.application.TaskService;
import com.teamproject.task.application.dto.TaskDtos.CreateTaskRequest;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TeamProjectApplication.class)
@Transactional
class AiAssistantActionServiceTest {
    @Autowired AiAssistantActionService service;
    @Autowired AiAssistantActionRepository actions;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskRepository tasks;
    @Autowired TaskService taskService;
    @Autowired TaskCommentRepository comments;
    @Autowired AiAssistantMessageStore messageStore;
    @Autowired NotificationRepository notifications;

    @Test
    void confirmedTaskCreationExecutesOnlyOnce() {
        Fixture fixture = fixture(true);
        AiAssistantAction action = actions.save(new AiAssistantAction(fixture.user(), fixture.group(),
                "create_task", """
                        {"title":"AI가 만든 업무","description":null,"priority":"HIGH",
                         "dueAt":null,"checklistItems":["테스트","모니터링"]}
                        """, "업무 생성: AI가 만든 업무", LocalDateTime.now().plusMinutes(10)));

        var first = service.confirm(fixture.user().getId(), action.getId());
        var second = service.confirm(fixture.user().getId(), action.getId());

        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(first.targetUrl()).startsWith("/tasks/");
        assertThat(second.message()).isEqualTo("이미 실행한 작업입니다.");
        assertThat(tasks.findAllByGroupIdOrderByCreatedAtDesc(fixture.group().getId()))
                .extracting("title").containsExactly("AI가 만든 업무");
    }

    @Test
    void inviteLinkStillRequiresLeaderAtConfirmationTime() {
        Fixture fixture = fixture(false);
        AiAssistantAction action = actions.save(new AiAssistantAction(fixture.user(), fixture.group(),
                "create_group_invite_link", "{}", "새 그룹 초대 링크 생성",
                LocalDateTime.now().plusMinutes(10)));

        assertThatThrownBy(() -> service.confirm(fixture.user().getId(), action.getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    var applicationException = (ApplicationException) exception;
                    assertThat(applicationException.code()).isEqualTo("AI_ASSISTANT_ACTION_FORBIDDEN");
                    assertThat(applicationException.getMessage()).isEqualTo("승인되지 않은 내용입니다.");
                });
    }

    @Test
    void selectsOnlyAnAccessibleWorkspaceAndPersistsConversationHistory() {
        Fixture fixture = fixture(true);
        Group target = groups.save(Group.personal(fixture.user()));
        members.save(GroupMember.leader(target, fixture.user()));
        AiAssistantAction action = actions.save(new AiAssistantAction(fixture.user(), fixture.group(),
                "select_workspace", "{\"groupId\":" + target.getId() + "}", "작업공간 선택",
                LocalDateTime.now().plusMinutes(10)));
        messageStore.append(fixture.user().getId(), fixture.group(), AiAssistantMessage.Role.USER,
                "개인 일정으로 바꿔줘", null);
        messageStore.append(fixture.user().getId(), fixture.group(), AiAssistantMessage.Role.ASSISTANT,
                "작업공간을 변경할까요?", action);

        var result = service.confirm(fixture.user().getId(), action.getId());
        var history = messageStore.list(fixture.user().getId(), fixture.group().getId());

        assertThat(result.selectedGroupId()).isEqualTo(target.getId());
        assertThat(history).extracting("content")
                .containsExactly("개인 일정으로 바꿔줘", "작업공간을 변경할까요?");
        assertThat(history.get(1).actionStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void createsACommentWithMentionThroughExistingPermissionChecks() {
        Fixture fixture = fixture(true);
        User mentionedUser = newUser("mentioned");
        GroupMember mentioned = members.save(GroupMember.member(fixture.group(), mentionedUser));
        var task = taskService.create(fixture.user().getId(), fixture.group().getId(),
                new CreateTaskRequest("댓글 대상 업무", null, "NORMAL", null, null));
        AiAssistantAction action = actions.save(new AiAssistantAction(fixture.user(), fixture.group(),
                "create_task_comment", "{\"taskId\":" + task.id()
                        + ",\"content\":\"@" + mentionedUser.getNickname()
                        + " 확인해 주세요\",\"mentionedMemberIds\":[" + mentioned.getId() + "]}",
                "댓글 작성", LocalDateTime.now().plusMinutes(10)));

        var result = service.confirm(fixture.user().getId(), action.getId());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(comments.findAllByTaskIdOrderByCreatedAtAscIdAsc(task.id()))
                .extracting("content").containsExactly("@" + mentionedUser.getNickname() + " 확인해 주세요");
    }

    @Test
    void sendsAnIdempotentNotificationOnlyToAnActiveMemberInTheSelectedGroup() {
        Fixture fixture = fixture(true);
        User recipientUser = newUser("recipient");
        GroupMember recipient = members.save(GroupMember.member(fixture.group(), recipientUser));
        AiAssistantAction action = actions.save(new AiAssistantAction(fixture.user(), fixture.group(),
                "send_group_notification", "{\"recipientMemberIds\":[" + recipient.getId()
                        + "],\"title\":\"확인 요청\",\"message\":\"업무를 확인해 주세요\"}",
                "알림 전송", LocalDateTime.now().plusMinutes(10)));

        service.confirm(fixture.user().getId(), action.getId());
        service.confirm(fixture.user().getId(), action.getId());

        var notification = notifications.findByRecipientIdAndEventKey(recipientUser.getId(),
                "AI_ASSISTANT_MESSAGE:" + action.getId()).orElseThrow();
        assertThat(notification.getType()).isEqualTo(Notification.Type.ASSISTANT_MESSAGE);
        assertThat(notification.getTitle()).isEqualTo("확인 요청");
    }

    private Fixture fixture(boolean leader) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        User user = users.save(new User("assistant_" + suffix,
                "assistant_" + suffix + "@example.com", "hash", "AI 사용자", true));
        Group group = groups.save(Group.team("AI 테스트 그룹", null, "Asia/Seoul", user));
        group.switchTestMembership(Group.MembershipPlan.PAID, LocalDateTime.now());
        members.save(leader ? GroupMember.leader(group, user) : GroupMember.member(group, user));
        return new Fixture(user, group);
    }

    private User newUser(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return users.save(new User(prefix + "_" + suffix,
                prefix + "_" + suffix + "@example.com", "hash", prefix, true));
    }

    private record Fixture(User user, Group group) {}
}
