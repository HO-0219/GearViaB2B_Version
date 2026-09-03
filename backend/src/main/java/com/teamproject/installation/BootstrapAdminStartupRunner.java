package com.teamproject.installation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Invokes installer bootstrap once, when an installer secret path is configured. */
@Component
public class BootstrapAdminStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminStartupRunner.class);
    private final BootstrapAdminService bootstrap;
    private final String secretFile;

    public BootstrapAdminStartupRunner(BootstrapAdminService bootstrap,
                                       @Value("${app.bootstrap-admin.secret-file:}") String secretFile) {
        this.bootstrap = bootstrap;
        this.secretFile = secretFile == null ? "" : secretFile.trim();
    }

    @Override public void run(ApplicationArguments args) {
        if (secretFile.isBlank() || !Files.isRegularFile(Path.of(secretFile))) {
            return;
        }
        try {
            bootstrap.bootstrap();
        } catch (IllegalStateException alreadyInitialised) {
            // A reinstall leaves the secret in place but users already exist; drop
            // the now-useless file instead of failing application startup.
            log.info("Bootstrap administrator already exists; discarding the installer secret.");
            try {
                Files.deleteIfExists(Path.of(secretFile));
            } catch (IOException ignored) {
                // never expose secret contents
            }
        }
    }
}
