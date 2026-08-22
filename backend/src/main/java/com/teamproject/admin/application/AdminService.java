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
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminService {
    private static final String INITIAL_PASSWORD = "user123";
    private final UserRepository users;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupReportDownloadRepository reportDownloads;
    private final ReportDeliveryRepository reportDeliveries;
    private final ReportScheduleRepository reportSchedules;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    public AdminService(UserRepository users, GroupRepository groups, GroupMemberRepository members,
            GroupReportDownloadRepository reportDownloads, ReportDeliveryRepository reportDeliveries,
            ReportScheduleRepository reportSchedules, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder) {
        this.users = users; this.groups = groups; this.members = members;
        this.reportDownloads = reportDownloads; this.reportDeliveries = reportDeliveries;
        this.reportSchedules = reportSchedules; this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
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
                user.getCreatedAt(), user.getLastLoginAt(), user.isForcePasswordChange())).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
    @Transactional
    public AdminUserResponse changeStatus(Long actorId, Long userId, String status) {
        if (actorId.equals(userId)) throw new ApplicationException("ADMIN_SELF_STATUS_FORBIDDEN", HttpStatus.CONFLICT, "본인 운영자 계정 상태는 변경할 수 없습니다.");
        User user = users.findById(userId).orElseThrow(() -> new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (user.getSystemRole() == User.SystemRole.ADMIN && "SUSPENDED".equalsIgnoreCase(status)
                && users.countByStatusAndSystemRole(User.Status.ACTIVE, User.SystemRole.ADMIN) <= 1)
            throw new ApplicationException("LAST_ADMIN_FORBIDDEN", HttpStatus.CONFLICT, "마지막 운영자 계정은 정지할 수 없습니다.");
        if ("SUSPENDED".equalsIgnoreCase(status)) {
            user.suspend(); user.invalidateSessions();
            refreshTokens.findAllByUserId(userId).forEach(RefreshToken::revoke);
        } else if ("ACTIVE".equalsIgnoreCase(status)) user.activate();
        else throw new ApplicationException("USER_STATUS_INVALID", HttpStatus.BAD_REQUEST, "계정 상태를 확인해 주세요.");
        return new AdminUserResponse(user.getId(), user.getUsername(), mask(user.getEmail()), user.getNickname(),
                user.getSystemRole().name(), user.getStatus().name(), user.getCreatedAt(), user.getLastLoginAt(), user.isForcePasswordChange());
    }
    @Transactional
    public AdminUserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        user.updateProfile(request.nickname().trim(), user.getPhoneNumber(), user.getProfileImageUrl());
        return toResponse(user);
    }
    @Transactional
    public void withdrawUser(Long actorId, Long userId) {
        if (actorId.equals(userId)) throw new ApplicationException("ADMIN_SELF_WITHDRAW_FORBIDDEN", HttpStatus.CONFLICT, "본인 운영자 계정은 삭제할 수 없습니다.");
        User user = users.findById(userId).orElseThrow(() -> new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (user.getSystemRole() == User.SystemRole.ADMIN
                && users.countByStatusAndSystemRole(User.Status.ACTIVE, User.SystemRole.ADMIN) <= 1)
            throw new ApplicationException("LAST_ADMIN_FORBIDDEN", HttpStatus.CONFLICT, "마지막 운영자 계정은 삭제할 수 없습니다.");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        user.anonymizeAndWithdraw("withdrawn_" + suffix, "withdrawn_" + suffix + "@withdrawn.local");
        user.invalidateSessions();
        refreshTokens.findAllByUserId(userId).forEach(RefreshToken::revoke);
    }
    @Transactional
    public TemporaryPasswordResponse createUser(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email))
            throw new ApplicationException("USER_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 사용 중인 계정입니다.");
        User user = new User(employeeUsername(), email, passwordEncoder.encode(INITIAL_PASSWORD), request.name().trim(), true);
        if ("ADMIN".equalsIgnoreCase(request.role())) user.promoteToAdmin();
        user.requirePasswordChange();
        return new TemporaryPasswordResponse(toResponse(users.save(user)), INITIAL_PASSWORD);
    }
    @Transactional
    public TemporaryPasswordResponse resetPassword(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        String temp = INITIAL_PASSWORD; user.changePassword(passwordEncoder.encode(temp)); user.requirePasswordChange(); user.invalidateSessions();
        refreshTokens.findAllByUserId(userId).forEach(RefreshToken::revoke);
        return new TemporaryPasswordResponse(toResponse(user), temp);
    }
    @Transactional public void endSessions(Long userId) { User u = users.findById(userId).orElseThrow(() -> new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")); u.invalidateSessions(); refreshTokens.findAllByUserId(userId).forEach(RefreshToken::revoke); }
    private String employeeUsername() {
        String username;
        do {
            username = "employee_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        } while (users.existsByUsernameIgnoreCase(username));
        return username;
    }
    private AdminUserResponse toResponse(User u) { return new AdminUserResponse(u.getId(),u.getUsername(),mask(u.getEmail()),u.getNickname(),u.getSystemRole().name(),u.getStatus().name(),u.getCreatedAt(),u.getLastLoginAt(),u.isForcePasswordChange()); }
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
