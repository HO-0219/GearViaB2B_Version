package com.teamproject.admin.application;

import com.teamproject.authentication.domain.LoginHistory;
import com.teamproject.authentication.domain.LoginHistoryRepository;
import com.teamproject.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminLoginHistoryServiceTest {
    private final LoginHistoryRepository history = mock(LoginHistoryRepository.class);
    private final AdminLoginHistoryService service = new AdminLoginHistoryService(history);

    @Test
    void listMapsSuccessEntriesWithTheLinkedUserId() {
        User user = new User("employee", "employee@example.com", "hash", "Employee", true);
        LoginHistory entry = LoginHistory.success(user, "127.0.0.1", "Chrome on Windows");
        when(history.findAllByOrderByOccurredAtDescIdDesc(any()))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 50), 1));

        var response = service.list(0, 50);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).outcome()).isEqualTo("SUCCESS");
        assertThat(response.items().get(0).ipAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void listMapsFailureEntriesWithoutAUserId() {
        LoginHistory entry = LoginHistory.failure("unknown-user", "10.0.0.5", null);
        when(history.findAllByOrderByOccurredAtDescIdDesc(any()))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 50), 1));

        var response = service.list(0, 50);

        assertThat(response.items().get(0).outcome()).isEqualTo("FAILURE");
        assertThat(response.items().get(0).userId()).isNull();
        assertThat(response.items().get(0).username()).isEqualTo("unknown-user");
    }
}
