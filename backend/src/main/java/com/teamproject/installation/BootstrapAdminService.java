package com.teamproject.installation;

import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/** Creates the first local administrator from an installer-owned, one-time file.
 * Format: one UTF-8 {@code key=value} per line; required keys are username, email, name, password.
 */
@Service
public class BootstrapAdminService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Path secretFile;

    public BootstrapAdminService(UserRepository users, PasswordEncoder passwordEncoder,
                                 @org.springframework.beans.factory.annotation.Value("${app.bootstrap-admin.secret-file:}") String secretFile) {
        this(users, passwordEncoder, secretFile == null || secretFile.isBlank() ? null : Path.of(secretFile));
    }

    BootstrapAdminService(UserRepository users, PasswordEncoder passwordEncoder, Path secretFile) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.secretFile = secretFile;
    }

    @Transactional
    public User bootstrap() {
        if (secretFile == null || !Files.isRegularFile(secretFile)) throw new IllegalStateException("Bootstrap secret file is unavailable.");
        if (users.count() != 0) throw new IllegalStateException("Bootstrap is only available before the first user.");
        Map<String, String> values = readSecret();
        String password = required(values, "password");
        User admin = new User(required(values, "username"), required(values, "email"), passwordEncoder.encode(password), required(values, "name"), true);
        admin.promoteToAdmin();
        User saved = users.save(admin);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            try { Files.deleteIfExists(secretFile); } catch (IOException ignored) { }
        } else TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { Files.deleteIfExists(secretFile); } catch (IOException ignored) { /* never expose secret contents */ }
            }
        });
        return saved;
    }

    private Map<String, String> readSecret() {
        try {
            return Files.readAllLines(secretFile).stream().filter(line -> !line.isBlank() && !line.trim().startsWith("#"))
                    .map(line -> line.split("=", 2)).filter(parts -> parts.length == 2)
                    .collect(Collectors.toUnmodifiableMap(parts -> parts[0].trim(), parts -> parts[1].trim()));
        } catch (IOException e) { throw new IllegalStateException("Bootstrap secret file cannot be read.", e); }
    }
    private String required(Map<String, String> values, String key) {
        String value = values.get(key); if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing bootstrap field: " + key);
        return value;
    }
}
