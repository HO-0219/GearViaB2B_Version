package com.teamproject.installation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Invokes installer bootstrap once, when an installer secret path is configured. */
@Component
public class BootstrapAdminStartupRunner implements ApplicationRunner {
    private final BootstrapAdminService bootstrap;
    private final String secretFile;

    public BootstrapAdminStartupRunner(BootstrapAdminService bootstrap,
                                       @Value("${app.bootstrap-admin.secret-file:}") String secretFile) {
        this.bootstrap = bootstrap;
        this.secretFile = secretFile == null ? "" : secretFile.trim();
    }

    @Override public void run(ApplicationArguments args) {
        if (!secretFile.isBlank()) bootstrap.bootstrap();
    }
}
