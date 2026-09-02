package com.teamproject.common.execution;

import com.teamproject.common.config.RuntimeTuningProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkloadExecutorsConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class, WorkloadExecutorsConfiguration.class)
            .withPropertyValues(
                    "app.runtime.database.maximum-pool-size=20",
                    "app.runtime.database.minimum-idle=5",
                    "app.runtime.database.connection-timeout-ms=30000",
                    "app.runtime.queries.max-task-results=1000",
                    "app.runtime.executors.document-index.core-size=1",
                    "app.runtime.executors.document-index.max-size=1",
                    "app.runtime.executors.document-index.queue-capacity=1",
                    "app.runtime.executors.document-index.keep-alive-seconds=60",
                    "app.runtime.executors.notification.core-size=1",
                    "app.runtime.executors.notification.max-size=1",
                    "app.runtime.executors.notification.queue-capacity=2",
                    "app.runtime.executors.notification.keep-alive-seconds=60",
                    "app.runtime.alerts.warning-percent=75",
                    "app.runtime.alerts.critical-percent=90");

    @Test
    void documentQueueCannotConsumeNotificationCapacity() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ThreadPoolTaskExecutor document = context.getBean("documentIndexExecutor", ThreadPoolTaskExecutor.class);
            ThreadPoolTaskExecutor notification = context.getBean("notificationExecutor", ThreadPoolTaskExecutor.class);
            CountDownLatch documentStarted = new CountDownLatch(1);
            CountDownLatch releaseDocument = new CountDownLatch(1);
            CountDownLatch notificationRan = new CountDownLatch(1);
            try {
                document.execute(() -> awaitRelease(documentStarted, releaseDocument));
                assertThat(documentStarted.await(2, TimeUnit.SECONDS)).isTrue();
                document.execute(() -> {});

                assertThatThrownBy(() -> document.execute(() -> {}))
                        .isInstanceOf(TaskRejectedException.class);
                notification.execute(notificationRan::countDown);
                assertThat(notificationRan.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(notification.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(2);
            } finally {
                releaseDocument.countDown();
            }
        });
    }

    @Test
    void telemetryReportsQueueCapacityAndRejections() {
        runner.run(context -> {
            ThreadPoolTaskExecutor document = context.getBean("documentIndexExecutor", ThreadPoolTaskExecutor.class);
            ExecutorTelemetry telemetry = context.getBean(ExecutorTelemetry.class);
            MeterRegistry meters = context.getBean(MeterRegistry.class);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try {
                document.execute(() -> awaitRelease(started, release));
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                document.execute(() -> {});
                assertThatThrownBy(() -> document.execute(() -> {})).isInstanceOf(TaskRejectedException.class);

                ExecutorTelemetry.ExecutorSnapshot snapshot = telemetry.snapshot("document-index");
                assertThat(snapshot.queueCapacity()).isEqualTo(1);
                assertThat(snapshot.queueSize()).isEqualTo(1);
                assertThat(snapshot.rejected()).isEqualTo(1);
                assertThat(meters.find("gearvia.executor.rejected").tag("workload", "document-index")
                        .counter().count()).isEqualTo(1.0);
            } finally {
                release.countDown();
            }
        });
    }

    private static void awaitRelease(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            release.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RuntimeTuningProperties.class)
    static class TestConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
