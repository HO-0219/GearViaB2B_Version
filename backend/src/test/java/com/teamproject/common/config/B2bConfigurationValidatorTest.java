package com.teamproject.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class B2bConfigurationValidatorTest {

    @Test
    void acceptsB2bProductionWithoutOauthPublicMailOrOpenAiKey() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("spring.security.oauth2.client.registration.google.client-id", "")
                .withProperty("spring.security.oauth2.client.registration.google.client-secret", "")
                .withProperty("app.mail.enabled", "false")
                .withProperty("spring.mail.host", "")
                .withProperty("spring.mail.username", "")
                .withProperty("spring.mail.password", "")
                .withProperty("app.mail.from", "no-reply@b2bgearvia.local")
                .withProperty("app.openai.api-key", "")
                .withProperty("app.ai-report.enabled", "false")
                .withProperty("app.ai-assistant.enabled", "false");

        assertThatCode(() -> new B2bConfigurationValidator(environment).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDefaultPasswordMissingJwtAndHostMySqlPort() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("spring.datasource.url",
                        "jdbc:mysql://localhost:3306/b2bgearvia?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul")
                .withProperty("spring.datasource.password", "test")
                .withProperty("app.jwt.secret", "");

        assertThatThrownBy(() -> new B2bConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_DATASOURCE_URL must use jdbc:mysql://mysql:3306/b2bgearvia")
                .hasMessageContaining("SPRING_DATASOURCE_PASSWORD must be a non-default secret of at least 16 characters")
                .hasMessageContaining("JWT_SECRET must be a non-default secret of at least 32 characters");
    }

    @Test
    void rejectsUnsafeB2bRuntimeFlags() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("app.demo.enabled", "true")
                .withProperty("app.jwt.secure-cookie", "false")
                .withProperty("spring.jpa.hibernate.ddl-auto", "update")
                .withProperty("app.storage.provider", "s3")
                .withProperty("app.storage.local-root", "/opt/b2bgearvia/uploads");

        assertThatThrownBy(() -> new B2bConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_ENABLED must be false")
                .hasMessageContaining("AUTH_SECURE_COOKIE must be true")
                .hasMessageContaining("SPRING_JPA_HIBERNATE_DDL_AUTO must be validate")
                .hasMessageContaining("STORAGE_PROVIDER must be local or nas_mount")
                .hasMessageContaining("UPLOAD_LOCAL_ROOT must be /opt/b2bgearvia/data/uploads");
    }

    @Test
    void acceptsNasMountStorageWithNasRootConfigured() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("app.storage.provider", "nas_mount")
                .withProperty("app.storage.nas-root", "/mnt/company-nas/b2bgearvia")
                .withProperty("spring.security.oauth2.client.registration.google.client-id", "")
                .withProperty("spring.security.oauth2.client.registration.google.client-secret", "")
                .withProperty("app.mail.enabled", "false")
                .withProperty("spring.mail.host", "")
                .withProperty("spring.mail.username", "")
                .withProperty("spring.mail.password", "")
                .withProperty("app.mail.from", "no-reply@b2bgearvia.local")
                .withProperty("app.openai.api-key", "")
                .withProperty("app.ai-report.enabled", "false")
                .withProperty("app.ai-assistant.enabled", "false");

        assertThatCode(() -> new B2bConfigurationValidator(environment).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNasMountStorageWithoutNasRoot() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("app.storage.provider", "nas_mount")
                .withProperty("app.storage.nas-root", "");

        assertThatThrownBy(() -> new B2bConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STORAGE_NAS_ROOT must be set when STORAGE_PROVIDER is nas_mount");
    }

    @Test
    void rejectsMissingOrDefaultAdminMfaSecret() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("app.admin.mfa-encryption-key-base64",
                        "Y2hhbmdlLW1lLWNoYW5nZS1tZS1jaGFuZ2UtbWUtY2hhbmdlLW1l");

        assertThatThrownBy(() -> new B2bConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "ADMIN_MFA_ENCRYPTION_KEY_BASE64 must decode to exactly 32 non-default bytes");
    }

    @Test
    void rejectsAdminMfaSecretThatDoesNotDecodeToExactly32Bytes() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("app.admin.mfa-encryption-key-base64",
                        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWZ4");

        assertThatThrownBy(() -> new B2bConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "ADMIN_MFA_ENCRYPTION_KEY_BASE64 must decode to exactly 32 non-default bytes");
    }

    @Test
    void rejectsWeakCommonDatabasePassword() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("spring.datasource.password", "passwordpassword");

        assertThatThrownBy(() -> new B2bConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_DATASOURCE_PASSWORD must be a non-default secret of at least 16 characters");
    }

    @Test
    void rejectsLowDiversityJwtSecret() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("app.jwt.secret", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        assertThatThrownBy(() -> new B2bConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be a non-default secret of at least 32 characters");
    }

    @Test
    void rejectsLowDiversityAdminMfaSecret() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("app.admin.mfa-encryption-key-base64",
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

        assertThatThrownBy(() -> new B2bConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "ADMIN_MFA_ENCRYPTION_KEY_BASE64 must decode to exactly 32 non-default bytes");
    }

    @Test
    void rejectsLegacyProductionEnvironmentName() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("app.environment", "production");

        assertThatThrownBy(() -> new B2bConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported APP_ENVIRONMENT: use b2b-production for deployments");
    }

    @Test
    void doesNotExposeSecretValuesInFailureMessages() {
        String dbPassword = "default-password-that-must-not-leak";
        String jwtSecret = "change-me-secret-that-must-not-leak-1234567890";
        String mfaSecret = "Y2hhbmdlLW1lLXNlY3JldC10aGF0LW11c3Qtbm90LWxlYWstMTIz";
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("spring.datasource.password", dbPassword)
                .withProperty("app.jwt.secret", jwtSecret)
                .withProperty("app.admin.mfa-encryption-key-base64", mfaSecret);

        assertThatThrownBy(() -> new B2bConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(dbPassword)
                .hasMessageNotContaining(jwtSecret)
                .hasMessageNotContaining(mfaSecret);
    }

    private MockEnvironment secureB2bProductionEnvironment() {
        return new MockEnvironment()
                .withProperty("app.environment", "b2b-production")
                .withProperty("app.frontend-url", "https://b2bgearvia.internal")
                .withProperty("spring.datasource.url",
                        "jdbc:mysql://mysql:3306/b2bgearvia?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul")
                .withProperty("spring.datasource.password", "B2gV8rN2pQ7sT4wX")
                .withProperty("app.jwt.secret", "B2gV8rN2pQ7sT4wX9zK3mH6cL5fA0yR1")
                .withProperty("app.jwt.secure-cookie", "true")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("app.storage.provider", "local")
                .withProperty("app.storage.local-root", "/opt/b2bgearvia/data/uploads")
                .withProperty("app.demo.enabled", "false")
                .withProperty("app.admin.mfa-encryption-key-base64",
                        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
}
