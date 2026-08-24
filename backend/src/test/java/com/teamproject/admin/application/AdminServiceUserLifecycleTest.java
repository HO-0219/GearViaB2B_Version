package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.UpdateUserRequest;
import com.teamproject.authentication.domain.token.RefreshTokenRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupReportDownloadRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.domain.ReportDeliveryRepository;
import com.teamproject.report.domain.ReportScheduleRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceUserLifecycleTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final AdminService service = new AdminService(users, mock(GroupRepository.class),
            mock(GroupMemberRepository.class), mock(GroupReportDownloadRepository.class),
            mock(ReportDeliveryRepository.class), mock(ReportScheduleRepository.class),
            refreshTokens, mock(PasswordEncoder.class));

    @Test
    void updateUserChangesTheNicknameOnly() {
        User user = new User("employee_1", "employee@company.com", "hash", "이전 이름", true);
        when(users.findById(10L)).thenReturn(Optional.of(user));

        var response = service.updateUser(10L, new UpdateUserRequest("새 이름"));

        assertThat(user.getNickname()).isEqualTo("새 이름");
        assertThat(response.nickname()).isEqualTo("새 이름");
    }

    @Test
    void withdrawAnonymizesTheAccountAndRevokesSessions() {
        User user = new User("employee_1", "employee@company.com", "hash", "홍길동", true);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        when(refreshTokens.findAllByUserId(10L)).thenReturn(List.of());

        service.withdrawUser(1L, 10L);

        assertThat(user.getStatus()).isEqualTo(User.Status.WITHDRAWN);
        assertThat(user.getUsername()).startsWith("withdrawn_");
        assertThat(user.getEmail()).contains("@withdrawn.local");
        verify(refreshTokens).findAllByUserId(10L);
    }

    @Test
    void adminCannotWithdrawTheirOwnAccount() {
        assertThatThrownBy(() -> service.withdrawUser(1L, 1L))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    void refusesToWithdrawTheLastActiveAdmin() {
        User lastAdmin = new User("admin_1", "admin@company.com", "hash", "관리자", true);
        lastAdmin.promoteToAdmin();
        when(users.findById(10L)).thenReturn(Optional.of(lastAdmin));
        when(users.countByStatusAndSystemRole(User.Status.ACTIVE, User.SystemRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.withdrawUser(1L, 10L))
                .isInstanceOf(ApplicationException.class);
    }
}
