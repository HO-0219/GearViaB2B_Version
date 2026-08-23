package com.teamproject.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@Component
public class B2bConfigurationValidator {
    private static final String B2B_PRODUCTION = "b2b-production";
    private static final String UPLOAD_ROOT = "/opt/b2bgearvia/data/uploads";

    private final Environment environment;

    public B2bConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        String stage = value("app.environment").toLowerCase(Locale.ROOT);
        if (stage.equals("local") || stage.equals("test")) return;
        if (!stage.equals(B2B_PRODUCTION)) {
            throw new IllegalStateException(
                    "Unsupported APP_ENVIRONMENT: use b2b-production for deployments and local/test for development");
        }

        List<String> failures = new ArrayList<>();
        require("app.frontend-url", value -> value.startsWith("https://"), failures,
                "FRONTEND_URL must use HTTPS");
        require("spring.datasource.url", this::usesInternalB2bDatabase, failures,
                "SPRING_DATASOURCE_URL must use jdbc:mysql://mysql:3306/b2bgearvia");
        require("spring.datasource.password", value -> isNonDefaultSecret(value, 16), failures,
                "SPRING_DATASOURCE_PASSWORD must be a non-default secret of at least 16 characters");
        require("app.jwt.secret", value -> isNonDefaultSecret(value, 32), failures,
                "JWT_SECRET must be a non-default secret of at least 32 characters");
        require("app.admin.mfa-encryption-key-base64", this::isNonDefaultBase64Key, failures,
                "ADMIN_MFA_ENCRYPTION_KEY_BASE64 must decode to exactly 32 non-default bytes");
        require("app.jwt.secure-cookie", Boolean::parseBoolean, failures,
                "AUTH_SECURE_COOKIE must be true");
        require("spring.jpa.hibernate.ddl-auto", value -> value.equalsIgnoreCase("validate"), failures,
                "SPRING_JPA_HIBERNATE_DDL_AUTO must be validate");
        require("app.storage.provider", value -> value.equalsIgnoreCase("local") || value.equalsIgnoreCase("nas_mount"),
                failures, "STORAGE_PROVIDER must be local or nas_mount");
        if (value("app.storage.provider").equalsIgnoreCase("nas_mount")) {
            require("app.storage.nas-root", value -> !value.isBlank(), failures,
                    "STORAGE_NAS_ROOT must be set when STORAGE_PROVIDER is nas_mount");
        } else {
            require("app.storage.local-root", value -> value.equals(UPLOAD_ROOT), failures,
                    "UPLOAD_LOCAL_ROOT must be /opt/b2bgearvia/data/uploads");
        }
        require("app.demo.enabled", value -> !Boolean.parseBoolean(value), failures,
                "DEMO_ENABLED must be false");

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Unsafe b2b-production configuration: " + String.join("; ", failures));
        }
    }

    private void require(String property, Predicate<String> predicate, List<String> failures, String message) {
        if (!predicate.test(value(property))) failures.add(message);
    }

    private String value(String property) {
        return environment.getProperty(property, "").trim();
    }

    private boolean usesInternalB2bDatabase(String value) {
        return value.matches("^jdbc:mysql://mysql:3306/b2bgearvia(?:[?;].*)?$");
    }

    private boolean isNonDefaultSecret(String value, int minLength) {
        return value.length() >= minLength
                && !looksLikeDefault(value)
                && !usesCommonRepeatedToken(value)
                && hasEnoughCharacterDiversity(value);
    }

    private boolean isNonDefaultBase64Key(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return decoded.length == 32
                    && !looksLikeDefault(value)
                    && !looksLikeDefault(new String(decoded, StandardCharsets.UTF_8))
                    && hasEnoughByteDiversity(decoded);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean looksLikeDefault(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.isBlank()
                || normalized.equals("test")
                || normalized.contains("change-me")
                || normalized.contains("changeme")
                || normalized.contains("default")
                || normalized.contains("placeholder")
                || normalized.contains("replace-with")
                || normalized.contains("local-production-test");
    }

    private boolean usesCommonRepeatedToken(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.matches("(password|admin|root|letmein|qwerty|welcome){2,}")
                || normalized.matches("(1234567890|0123456789){2,}");
    }

    private boolean hasEnoughCharacterDiversity(String value) {
        long distinctCharacters = value.chars().distinct().count();
        long mostCommonCharacterCount = value.chars()
                .mapToLong(character -> value.chars().filter(candidate -> candidate == character).count())
                .max()
                .orElse(0);
        return distinctCharacters >= 4 && mostCommonCharacterCount <= value.length() * 3L / 4L;
    }

    private boolean hasEnoughByteDiversity(byte[] value) {
        int[] counts = new int[256];
        int distinctBytes = 0;
        int mostCommonByteCount = 0;
        for (byte item : value) {
            int index = item & 0xff;
            if (counts[index] == 0) distinctBytes++;
            counts[index]++;
            mostCommonByteCount = Math.max(mostCommonByteCount, counts[index]);
        }
        return distinctBytes >= 4 && mostCommonByteCount <= value.length * 3 / 4;
    }
}
