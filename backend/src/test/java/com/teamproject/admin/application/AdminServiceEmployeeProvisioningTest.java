package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.CreateUserRequest;
import com.teamproject.authentication.domain.token.RefreshTokenRepository;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupReportDownloadRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.domain.ReportDeliveryRepository;
import com.teamproject.report.domain.ReportScheduleRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminServiceEmployeeProvisioningTest {

    @Test
    void createsAnEmployeeFromNameAndEmailWithTheSharedInitialPassword() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AdminService service = new AdminService(users, mock(GroupRepository.class),
                mock(GroupMemberRepository.class), mock(GroupReportDownloadRepository.class),
                mock(ReportDeliveryRepository.class), mock(ReportScheduleRepository.class),
                mock(RefreshTokenRepository.class), passwordEncoder);
        when(passwordEncoder.encode("user123")).thenReturn("encoded-user123");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createUser(new CreateUserRequest("employee@company.com", "홍길동", "USER"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(users).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getUsername()).startsWith("employee_");
        assertThat(saved.getEmail()).isEqualTo("employee@company.com");
        assertThat(saved.getName()).isEqualTo("홍길동");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-user123");
        assertThat(saved.isForcePasswordChange()).isTrue();
        assertThat(response.temporaryPassword()).isEqualTo("user123");
    }
}
