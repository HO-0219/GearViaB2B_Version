package com.teamproject.common.config;

import com.teamproject.common.runtime.InstanceIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeTuningPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(safeLimits());

    @Test
    void bindsSafeLimits() {
        runner.withPropertyValues(
                        "app.runtime.queries.max-task-results=750",
                        "app.runtime.executors.document-index.core-size=2",
                        "app.runtime.executors.document-index.max-size=4",
                        "app.runtime.executors.document-index.queue-capacity=100")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RuntimeTuningProperties properties = context.getBean(RuntimeTuningProperties.class);
                    assertThat(properties.queries().maxTaskResults()).isEqualTo(750);
                    assertThat(properties.executors().documentIndex().maxSize()).isEqualTo(4);
                });
    }

    @Test
    void rejectsUnboundedExecutorQueue() {
        runner.withPropertyValues("app.runtime.executors.document-index.queue-capacity=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsCrossFieldLimitInversions() {
        runner.withPropertyValues(
                        "app.runtime.database.minimum-idle=21",
                        "app.runtime.database.maximum-pool-size=20")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(
                        "app.runtime.executors.notification.core-size=5",
                        "app.runtime.executors.notification.max-size=4")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(
                        "app.runtime.alerts.warning-percent=90",
                        "app.runtime.alerts.critical-percent=90")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void instanceIdentityUsesConfiguredValueOrGeneratesOneOnce() {
        assertThat(new InstanceIdentity(" backend-1 ").value()).isEqualTo("backend-1");
        InstanceIdentity generated = new InstanceIdentity(" ");
        assertThat(generated.value()).isNotBlank();
        assertThat(generated.value()).isEqualTo(generated.value());
    }

    private static String[] safeLimits() {
        return new String[] {
                "app.runtime.database.maximum-pool-size=20",
                "app.runtime.database.minimum-idle=5",
                "app.runtime.database.connection-timeout-ms=30000",
                "app.runtime.queries.max-task-results=1000",
                "app.runtime.executors.document-index.core-size=1",
                "app.runtime.executors.document-index.max-size=2",
                "app.runtime.executors.document-index.queue-capacity=100",
                "app.runtime.executors.document-index.keep-alive-seconds=60",
                "app.runtime.executors.notification.core-size=2",
                "app.runtime.executors.notification.max-size=4",
                "app.runtime.executors.notification.queue-capacity=500",
                "app.runtime.executors.notification.keep-alive-seconds=60",
                "app.runtime.alerts.warning-percent=75",
                "app.runtime.alerts.critical-percent=90"
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RuntimeTuningProperties.class)
    static class PropertiesConfiguration {}
}
