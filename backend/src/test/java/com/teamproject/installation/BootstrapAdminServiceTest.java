package com.teamproject.installation;

import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BootstrapAdminServiceTest {
    @TempDir Path tempDir;

    @Test
    void createsLocalAdminFromOneTimeFileOnlyWhenUserTableIsEmpty() throws Exception {
        Path secret = tempDir.resolve("bootstrap-admin.env");
        Files.writeString(secret, "username=admin\nemail=admin@example.test\nname=System Admin\npassword=CorrectHorseBatteryStaple!\n");
        UserRepository users = mock(UserRepository.class);
        when(users.count()).thenReturn(0L);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BootstrapAdminService service = new BootstrapAdminService(users, new BCryptPasswordEncoder(), secret);

        User admin = service.bootstrap();

        assertThat(admin.getSystemRole()).isEqualTo(User.SystemRole.ADMIN);
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getPasswordHash()).isNotEqualTo("CorrectHorseBatteryStaple!");
        assertThat(new BCryptPasswordEncoder().matches("CorrectHorseBatteryStaple!", admin.getPasswordHash())).isTrue();
        verify(users).save(admin);
        assertThat(Files.exists(secret)).isFalse();
    }
}
