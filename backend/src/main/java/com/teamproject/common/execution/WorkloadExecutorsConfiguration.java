package com.teamproject.common.execution;

import com.teamproject.common.config.RuntimeTuningProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class WorkloadExecutorsConfiguration {
    @Bean
    ExecutorTelemetry executorTelemetry(MeterRegistry meters) {
        return new ExecutorTelemetry(meters);
    }

    @Bean(name = "documentIndexExecutor")
    ThreadPoolTaskExecutor documentIndexExecutor(RuntimeTuningProperties properties,
            ExecutorTelemetry telemetry) {
        return executor("document-index", "gearvia-document-index-",
                properties.executors().documentIndex(), telemetry);
    }

    @Bean(name = "notificationExecutor")
    ThreadPoolTaskExecutor notificationExecutor(RuntimeTuningProperties properties,
            ExecutorTelemetry telemetry) {
        return executor("notification", "gearvia-notification-",
                properties.executors().notification(), telemetry);
    }

    private ThreadPoolTaskExecutor executor(String name, String threadPrefix,
            RuntimeTuningProperties.Executor limits, ExecutorTelemetry telemetry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(limits.coreSize());
        executor.setMaxPoolSize(limits.maxSize());
        executor.setQueueCapacity(limits.queueCapacity());
        executor.setKeepAliveSeconds(limits.keepAliveSeconds());
        executor.setThreadNamePrefix(threadPrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(telemetry.register(name, executor));
        return executor;
    }
}
