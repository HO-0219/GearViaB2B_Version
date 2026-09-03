package com.teamproject.installation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BootstrapAdminStartupRunnerTest {
    @TempDir Path tempDir;

    @Test
    void skipsBootstrapAfterOneTimeSecretHasBeenDeleted() throws Exception {
        BootstrapAdminService bootstrap = mock(BootstrapAdminService.class);
        Path deletedSecret = tempDir.resolve("admin.env");
        BootstrapAdminStartupRunner runner = new BootstrapAdminStartupRunner(bootstrap, deletedSecret.toString());

        runner.run(new DefaultApplicationArguments());

        verify(bootstrap, never()).bootstrap();
    }

    @Test
    void bootstrapsWhenOneTimeSecretExists() throws Exception {
        BootstrapAdminService bootstrap = mock(BootstrapAdminService.class);
        Path secret = Files.writeString(tempDir.resolve("admin.env"), "password=temporary");
        BootstrapAdminStartupRunner runner = new BootstrapAdminStartupRunner(bootstrap, secret.toString());

        runner.run(new DefaultApplicationArguments());

        verify(bootstrap).bootstrap();
    }

    @Test
    void reinstallDiscardsTheSecretInsteadOfFailingStartupWhenUsersAlreadyExist() throws Exception {
        BootstrapAdminService bootstrap = mock(BootstrapAdminService.class);
        doThrow(new IllegalStateException("Bootstrap is only available before the first user."))
                .when(bootstrap).bootstrap();
        Path secret = Files.writeString(tempDir.resolve("admin.env"), "password=temporary");
        BootstrapAdminStartupRunner runner = new BootstrapAdminStartupRunner(bootstrap, secret.toString());

        assertThatCode(() -> runner.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        assertThat(Files.exists(secret)).isFalse();
    }
}
