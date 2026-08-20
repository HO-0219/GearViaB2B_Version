package com.teamproject.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ProductionConfigurationValidatorTest {

    @Test
    void delegatesCompatibilityValidationToB2bRules() {
        MockEnvironment environment = secureB2bProductionEnvironment();

        assertThatCode(() -> new ProductionConfigurationValidator(environment).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void compatibilityWrapperRejectsLegacySaasProductionConfiguration() {
        MockEnvironment environment = secureB2bProductionEnvironment()
                .withProperty("app.environment", "production");

        assertThatThrownBy(() -> new ProductionConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported APP_ENVIRONMENT: use b2b-production for deployments");
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
