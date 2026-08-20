package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.*;
import com.teamproject.authentication.domain.token.RefreshToken;
import com.teamproject.authentication.domain.token.RefreshTokenRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.*;
import com.teamproject.report.domain.*;
import com.teamproject.user.domain.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final UserRepository users;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupReportDownloadRepository reportDownloads;
    private final ReportDeliveryRepository reportDeliveries;
    private final ReportScheduleRepository reportSchedules;
    private final RefreshTokenRepository refreshTokens;
    public AdminService(UserRepository users, GroupRepository groups, GroupMemberRepository members,
            GroupReportDownloadRepository reportDownloads, ReportDeliveryRepository reportDeliveries,
            ReportScheduleRepository reportSchedules, RefreshTokenRepository refreshTokens) {
        this.users = users; this.groups = groups; this.members = members;
        this.reportDownloads = reportDownloads; this.reportDeliveries = reportDeliveries;
        this.reportSchedules = reportSchedules; this.refreshTokens = refreshTokens;
    }
    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        return new OverviewResponse(users.count(), users.countByStatus(User.Status.ACTIVE),
                users.countByStatus(User.Status.SUSPENDED), groups.count(),
                groups.findAllByTypeOrderByCreatedAtDesc(Group.Type.TEAM).size(),
                reportDownloads.count(), reportDeliveries.count(),
                reportDeliveries.countByStatus(ReportDelivery.Status.FAILED));
    }
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> users(int page, int size) {
        var result = users.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage(page), safeSize(size)));
        return new PageResponse<>(result.map(user -> new AdminUserResponse(user.getId(), user.getUsername(),
                mask(user.getEmail()), user.getNickname(), user.getSystemRole().name(), user.getStatus().name(),
                user.getCreatedAt(), user.getLastLoginAt())).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
    @Transactional
    public AdminUserResponse changeStatus(Long actorId, Long userId, String status) {
        if (actorId.equals(userId)) throw new ApplicationException("ADMIN_SELF_STATUS_FORBIDDEN", HttpStatus.CONFLICT, "본인 운영자 계정 상태는 변경할 수 없습니다.");
        User user = users.findById(userId).orElseThrow(() -> new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (user.getSystemRole() == User.SystemRole.ADMIN) throw new ApplicationException("ADMIN_STATUS_FORBIDDEN", HttpStatus.FORBIDDEN, "다른 운영자 계정 상태는 변경할 수 없습니다.");
        if ("SUSPENDED".equalsIgnoreCase(status)) {
            user.suspend(); user.invalidateSessions();
            refreshTokens.findAllByUserId(userId).forEach(RefreshToken::revoke);
        } else if ("ACTIVE".equalsIgnoreCase(status)) user.activate();
        else throw new ApplicationException("USER_STATUS_INVALID", HttpStatus.BAD_REQUEST, "계정 상태를 확인해 주세요.");
        return new AdminUserResponse(user.getId(), user.getUsername(), mask(user.getEmail()), user.getNickname(),
                user.getSystemRole().name(), user.getStatus().name(), user.getCreatedAt(), user.getLastLoginAt());
    }
    @Transactional(readOnly = true)
    public PageResponse<AdminGroupResponse> groups(int page, int size) {
        var result = groups.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage(page), safeSize(size)));
        var scheduleByGroup = reportSchedules.findAll().stream().collect(java.util.stream.Collectors.toMap(
                value -> value.getGroup().getId(), value -> value, (left, right) -> left));
        var groupIds = result.getContent().stream().map(Group::getId).toList();
        var memberCountByGroup = groupIds.isEmpty() ? java.util.Map.<Long, Long>of()
                : members.countByGroupIdsAndStatus(groupIds, GroupMember.Status.ACTIVE).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                GroupMemberRepository.GroupMemberCount::getGroupId,
                                GroupMemberRepository.GroupMemberCount::getMemberCount));
        var items = result.getContent().stream().map(group -> {
            ReportSchedule schedule = scheduleByGroup.get(group.getId());
            return new AdminGroupResponse(group.getId(), group.getName(), group.getType().name(),
                    group.getMembershipPlan().name(),
                    memberCountByGroup.getOrDefault(group.getId(), 0L),
                    schedule != null && schedule.isActive(), group.getCreatedAt());
        }).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true)
    public PageResponse<AdminReportDownloadResponse> reportDownloads(int page, int size) {
        var result = reportDownloads.findAllByOrderByCreatedAtDesc(
                PageRequest.of(safePage(page), safeSize(size)));
        return new PageResponse<>(result.map(value -> new AdminReportDownloadResponse(value.getId(),
                value.getGroup().getId(), value.getGroup().getName(),
                value.getRequestedBy().getUser().getId(), value.getScope().name(),
                value.getPeriodType().name(), value.getCreatedAt())).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true)
    public PageResponse<AdminReportDeliveryResponse> reportDeliveries(int page, int size) {
        var result = reportDeliveries.findAllByOrderByCreatedAtDesc(
                PageRequest.of(safePage(page), safeSize(size)));
        return new PageResponse<>(result.map(value -> new AdminReportDeliveryResponse(value.getId(),
                value.getSchedule().getGroup().getId(), value.getSchedule().getGroup().getName(),
                value.getPeriodType().name(), value.getLanguage().name(), value.getStatus().name(),
                value.getRetryCount(), value.getErrorCode(), value.getLastAttemptAt(), value.getNextRetryAt(),
                value.getSentAt(), value.getCreatedAt())).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    private int safePage(int page) { return Math.max(0, page); }
    private int safeSize(int size) { return Math.min(100, Math.max(1, size)); }
    private String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.substring(0, 1) + "***" + email.substring(at);
    }
}
