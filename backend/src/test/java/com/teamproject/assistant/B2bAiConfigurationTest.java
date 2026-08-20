package com.teamproject.assistant;

import com.teamproject.assistant.infrastructure.openai.OpenAiAssistantProperties;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies optional AI configuration states without making a network request. */
class B2bAiConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void noKeyStartsWithBothFeaturesDisabled() {
        runner.run(context -> {
            OpenAiReportProperties report = context.getBean(OpenAiReportProperties.class);
            OpenAiAssistantProperties assistant = context.getBean(OpenAiAssistantProperties.class);
            assertThat(report.enabled()).isFalse();
            assertThat(report.hasApiKey()).isFalse();
            assertThat(assistant.enabled()).isFalse();
        });
    }

    @Test
    void configuredKeyAndFlagsEnableBothFeatures() {
        runner.withPropertyValues("app.ai-report.enabled=true", "app.ai-report.api-key=placeholder-test-key",
                        "app.ai-report.model=test-model", "app.ai-assistant.enabled=true",
                        "app.ai-assistant.model=test-assistant")
                .run(context -> {
                    assertThat(context.getBean(OpenAiReportProperties.class).enabled()).isTrue();
                    assertThat(context.getBean(OpenAiReportProperties.class).hasApiKey()).isTrue();
                    assertThat(context.getBean(OpenAiAssistantProperties.class).enabled()).isTrue();
                });
    }

    @Test
    void removingKeyDisablesEffectiveReportConfiguration() {
        runner.withPropertyValues("app.ai-report.enabled=true", "app.ai-report.api-key= ")
                .run(context -> {
                    OpenAiReportProperties report = context.getBean(OpenAiReportProperties.class);
                    assertThat(report.enabled()).isTrue();
                    assertThat(report.hasApiKey()).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({OpenAiReportProperties.class, OpenAiAssistantProperties.class})
    static class PropertiesConfiguration {}
}
