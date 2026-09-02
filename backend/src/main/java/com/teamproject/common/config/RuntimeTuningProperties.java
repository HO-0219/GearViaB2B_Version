package com.teamproject.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.runtime")
public record RuntimeTuningProperties(
        @NotNull @Valid Database database,
        @NotNull @Valid Queries queries,
        @NotNull @Valid Executors executors,
        @NotNull @Valid Alerts alerts) {

    public record Database(
            @Min(2) @Max(200) int maximumPoolSize,
            @Min(1) @Max(199) int minimumIdle,
            @Min(1000) long connectionTimeoutMs) {
        public Database {
            if (minimumIdle > maximumPoolSize) {
                throw new IllegalArgumentException("database minimum-idle must not exceed maximum-pool-size");
            }
        }
    }

    public record Queries(@Min(50) @Max(5000) int maxTaskResults) {}

    public record Executor(
            @Min(1) int coreSize,
            @Min(1) int maxSize,
            @Min(1) int queueCapacity,
            @Min(1) int keepAliveSeconds) {
        public Executor {
            if (coreSize > maxSize) {
                throw new IllegalArgumentException("executor core-size must not exceed max-size");
            }
        }
    }

    public record Executors(
            @NotNull @Valid Executor documentIndex,
            @NotNull @Valid Executor notification) {}

    public record Alerts(
            @DecimalMin("50") @DecimalMax("100") double warningPercent,
            @DecimalMin("50") @DecimalMax("100") double criticalPercent) {
        public Alerts {
            if (warningPercent >= criticalPercent) {
                throw new IllegalArgumentException("warning-percent must be lower than critical-percent");
            }
        }
    }
}
