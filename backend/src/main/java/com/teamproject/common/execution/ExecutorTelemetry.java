package com.teamproject.common.execution;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

public final class ExecutorTelemetry {
    private final MeterRegistry meters;
    private final Map<String, Registration> registrations = new ConcurrentHashMap<>();

    public ExecutorTelemetry(MeterRegistry meters) {
        this.meters = meters;
    }

    public RejectedExecutionHandler register(String name, ThreadPoolTaskExecutor executor) {
        AtomicLong rejected = new AtomicLong();
        Counter rejectionCounter = Counter.builder("gearvia.executor.rejected")
                .tag("workload", name)
                .register(meters);
        Registration registration = new Registration(executor, rejected);
        if (registrations.putIfAbsent(name, registration) != null) {
            throw new IllegalStateException("Executor telemetry already registered: " + name);
        }
        registerGauges(name, executor);
        return (task, pool) -> {
            rejected.incrementAndGet();
            rejectionCounter.increment();
            new ThreadPoolExecutor.AbortPolicy().rejectedExecution(task, pool);
        };
    }

    public ExecutorSnapshot snapshot(String name) {
        Registration registration = registrations.get(name);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown executor workload: " + name);
        }
        return snapshot(name, registration);
    }

    public List<ExecutorSnapshot> snapshots() {
        return registrations.entrySet().stream()
                .map(entry -> snapshot(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ExecutorSnapshot::name))
                .toList();
    }

    private ExecutorSnapshot snapshot(String name, Registration registration) {
        ThreadPoolTaskExecutor executor = registration.executor();
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        return new ExecutorSnapshot(name, pool.getActiveCount(), pool.getPoolSize(), pool.getCorePoolSize(),
                pool.getMaximumPoolSize(), pool.getQueue().size(),
                pool.getQueue().size() + pool.getQueue().remainingCapacity(),
                pool.getCompletedTaskCount(), registration.rejected().get());
    }

    private void registerGauges(String name, ThreadPoolTaskExecutor executor) {
        Gauge.builder("gearvia.executor.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                .tag("workload", name).register(meters);
        Gauge.builder("gearvia.executor.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize)
                .tag("workload", name).register(meters);
        Gauge.builder("gearvia.executor.queue.size", executor,
                        value -> value.getThreadPoolExecutor().getQueue().size())
                .tag("workload", name).register(meters);
        Gauge.builder("gearvia.executor.queue.capacity", executor,
                        value -> value.getThreadPoolExecutor().getQueue().size()
                                + value.getThreadPoolExecutor().getQueue().remainingCapacity())
                .tag("workload", name).register(meters);
        FunctionCounter.builder("gearvia.executor.completed", executor,
                        value -> value.getThreadPoolExecutor().getCompletedTaskCount())
                .tag("workload", name).register(meters);
    }

    private record Registration(ThreadPoolTaskExecutor executor, AtomicLong rejected) {}

    public record ExecutorSnapshot(String name, int active, int poolSize, int coreSize, int maxSize,
            int queueSize, int queueCapacity, long completed, long rejected) {}
}
