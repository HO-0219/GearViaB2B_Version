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
        Files.writeString(secret, "username=admin\nemail=admin@b2bgearvia.local\nname=B2BGearVia 관리자\npassword=admin\n");
        UserRepository users = mock(UserRepository.class);
        when(users.count()).thenReturn(0L);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BootstrapAdminService service = new BootstrapAdminService(users, new BCryptPasswordEncoder(), secret.toString());

        User admin = service.bootstrap();

        assertThat(admin.getSystemRole()).isEqualTo(User.SystemRole.ADMIN);
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getPasswordHash()).isNotEqualTo("admin");
        assertThat(new BCryptPasswordEncoder().matches("admin", admin.getPasswordHash())).isTrue();
        assertThat(admin.isForcePasswordChange()).isTrue();
        verify(users).save(admin);
        assertThat(Files.exists(secret)).isFalse();
    }
}
