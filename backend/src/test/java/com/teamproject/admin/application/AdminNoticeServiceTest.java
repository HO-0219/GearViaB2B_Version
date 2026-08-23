package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.CreateAdminNoticeRequest;
import com.teamproject.admin.domain.AdminNotice;
import com.teamproject.admin.domain.AdminNoticeRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminNoticeServiceTest {
    private final AdminNoticeRepository notices = mock(AdminNoticeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AdminNoticeService service = new AdminNoticeService(notices, users, members, notifications);

    private User admin() {
        User user = new User("admin", "admin@example.com", "hash", "Admin", true);
        user.promoteToAdmin();
        return user;
    }

    @Test
    void createRejectsAnUnknownActor() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L,
                new CreateAdminNoticeRequest("t", "m", LocalDateTime.now())))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    void createTrimsTitleAndMessageAndSavesAsPending() {
        when(users.findById(1L)).thenReturn(Optional.of(admin()));
        when(notices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime scheduledAt = LocalDateTime.now().plusHours(1);

        var response = service.create(1L, new CreateAdminNoticeRequest("  제목  ", "  내용  ", scheduledAt));

        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.message()).isEqualTo("내용");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void deliverDueDoesNothingWhenNoNoticeIsDue() {
        when(notices.findAllByStatusAndScheduledAtLessThanEqual(eq(AdminNotice.Status.PENDING), any()))
                .thenReturn(List.of());

        service.deliverDue();

        verifyNoInteractions(members, notifications);
    }

    @Test
    void deliverDueSendsToEveryActiveTeamLeaderAndMarksTheNoticeSent() {
        AdminNotice notice = new AdminNotice("공지", "내용", LocalDateTime.now().minusMinutes(1), admin());
        when(notices.findAllByStatusAndScheduledAtLessThanEqual(eq(AdminNotice.Status.PENDING), any()))
                .thenReturn(List.of(notice));
        User leader = new User("leader", "leader@example.com", "hash", "Leader", true);
        when(members.findDistinctActiveTeamLeaderUsers()).thenReturn(List.of(leader));
        when(notifications.adminNotice(eq(List.of(leader)), eq("ADMIN_NOTICE:null"), eq("공지"), eq("내용")))
                .thenReturn(1);

        service.deliverDue();

        assertThat(notice.getStatus()).isEqualTo(AdminNotice.Status.SENT);
        assertThat(notice.getRecipientCount()).isEqualTo(1);
        assertThat(notice.getSentAt()).isNotNull();
    }

    @Test
    void cancelRejectsANoticeThatIsNoLongerPending() {
        when(notices.findByIdAndStatus(5L, AdminNotice.Status.PENDING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(5L)).isInstanceOf(ApplicationException.class);
    }

    @Test
    void cancelMarksAPendingNoticeCancelled() {
        AdminNotice notice = new AdminNotice("공지", "내용", LocalDateTime.now().plusHours(1), admin());
        when(notices.findByIdAndStatus(5L, AdminNotice.Status.PENDING)).thenReturn(Optional.of(notice));

        service.cancel(5L);

        assertThat(notice.getStatus()).isEqualTo(AdminNotice.Status.CANCELLED);
    }
}
