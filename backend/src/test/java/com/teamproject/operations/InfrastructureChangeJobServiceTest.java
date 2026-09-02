package com.teamproject.operations;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.operations.application.InfrastructureChangeJobService;
import com.teamproject.operations.domain.InfrastructureChangeJob.Status;
import com.teamproject.operations.domain.InfrastructureChangeJob.Type;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class InfrastructureChangeJobServiceTest {
    @Autowired InfrastructureChangeJobService service;
    @Autowired UserRepository users;

    private Long adminId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User admin = new User("infra_" + suffix, "infra_" + suffix + "@example.com", "hash", "Infra Admin", true);
        admin.promoteToAdmin();
        adminId = users.save(admin).getId();
    }

    @Test
    void allowsApprovedForwardTransition() {
        var job = service.create(Type.MYSQL, adminId, "mysql.internal:3306/b2bgearvia", 900, correlation());

        var testing = service.transition(job.id(), job.version(), Status.TESTING, 0, null);

        assertThat(testing.status()).isEqualTo(Status.TESTING);
        assertThat(testing.startedAt()).isNotNull();
    }

    @Test
    void rejectsSkippedTransition() {
        var job = service.create(Type.STORAGE, adminId, "/opt/b2bgearvia/data/nas", 600, correlation());

        assertThatThrownBy(() -> service.transition(job.id(), job.version(), Status.MIGRATING, 10, null))
                .isInstanceOf(ApplicationException.class)
                .satisfies(error -> assertThat(((ApplicationException) error).code())
                        .isEqualTo("INFRASTRUCTURE_CHANGE_TRANSITION_INVALID"));
    }

    @Test
    void rejectsStaleVersion() {
        var job = service.create(Type.MYSQL, adminId, "mysql.internal", 900, correlation());
        service.transition(job.id(), job.version(), Status.TESTING, 0, null);

        assertThatThrownBy(() -> service.transition(job.id(), job.version(), Status.TEST_SUCCEEDED, 10, null))
                .isInstanceOf(ApplicationException.class)
                .satisfies(error -> assertThat(((ApplicationException) error).code())
                        .isEqualTo("INFRASTRUCTURE_CHANGE_VERSION_CONFLICT"));
    }

    @Test
    void supportsFailureAndExplicitRollbackPath() {
        var job = service.create(Type.STORAGE, adminId, "/opt/b2bgearvia/data/nas", 600, correlation());
        var testing = service.transition(job.id(), job.version(), Status.TESTING, 0, null);
        var failed = service.transition(testing.id(), testing.version(), Status.FAILED, 5, "NAS preflight failed");
        var rollingBack = service.transition(failed.id(), failed.version(), Status.ROLLING_BACK, 5, null);
        var rolledBack = service.transition(rollingBack.id(), rollingBack.version(), Status.ROLLED_BACK, 100,
                "Active storage remained unchanged");

        assertThat(rolledBack.status()).isEqualTo(Status.ROLLED_BACK);
        assertThat(rolledBack.completedAt()).isNotNull();
    }

    private String correlation() {
        return "change-" + UUID.randomUUID();
    }
}
