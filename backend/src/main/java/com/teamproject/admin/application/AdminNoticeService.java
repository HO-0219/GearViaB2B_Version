package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.AdminNoticeResponse;
import com.teamproject.admin.application.dto.AdminDtos.CreateAdminNoticeRequest;
import com.teamproject.admin.application.dto.AdminDtos.PageResponse;
import com.teamproject.admin.domain.AdminNotice;
import com.teamproject.admin.domain.AdminNoticeRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/** Lets an admin queue a broadcast to every active team leader; {@link AdminNoticeScheduler} triggers delivery. */
@Service
public class AdminNoticeService {
    private final AdminNoticeRepository notices;
    private final UserRepository users;
    private final GroupMemberRepository members;
    private final NotificationService notifications;

    public AdminNoticeService(AdminNoticeRepository notices, UserRepository users,
            GroupMemberRepository members, NotificationService notifications) {
        this.notices = notices;
        this.users = users;
        this.members = members;
        this.notifications = notifications;
    }

    @Transactional
    public AdminNoticeResponse create(Long actorId, CreateAdminNoticeRequest request) {
        User actor = users.findById(actorId).orElseThrow(() -> new ApplicationException(
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        AdminNotice notice = notices.save(new AdminNotice(request.title().trim(), request.message().trim(),
                request.scheduledAt(), actor));
        return response(notice);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminNoticeResponse> list(int page, int size) {
        var result = notices.findAllByOrderByScheduledAtDesc(
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return new PageResponse<>(result.map(this::response).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public void deliverDue() {
        List<AdminNotice> due = notices.findAllByStatusAndScheduledAtLessThanEqual(
                AdminNotice.Status.PENDING, LocalDateTime.now());
        if (due.isEmpty()) return;
        var recipients = members.findDistinctActiveTeamLeaderUsers();
        for (AdminNotice notice : due) {
            int count = notifications.adminNotice(recipients, "ADMIN_NOTICE:" + notice.getId(),
                    notice.getTitle(), notice.getMessage());
            notice.markSent(count);
        }
    }

    @Transactional
    public void cancel(Long noticeId) {
        AdminNotice notice = notices.findByIdAndStatus(noticeId, AdminNotice.Status.PENDING)
                .orElseThrow(() -> new ApplicationException("ADMIN_NOTICE_NOT_CANCELLABLE", HttpStatus.CONFLICT,
                        "발송 대기 중인 공지만 취소할 수 있습니다."));
        notice.cancel();
    }

    private AdminNoticeResponse response(AdminNotice value) {
        return new AdminNoticeResponse(value.getId(), value.getTitle(), value.getMessage(), value.getScheduledAt(),
                value.getStatus().name(), value.getRecipientCount(), value.getCreatedAt(), value.getSentAt());
    }
}
