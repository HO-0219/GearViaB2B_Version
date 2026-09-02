package com.teamproject.common.execution;

import com.teamproject.common.runtime.InstanceIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MetricsConfiguration {
    @Bean
    MeterRegistryCustomizer<MeterRegistry> instanceCommonTag(InstanceIdentity identity) {
        return registry -> registry.config().commonTags("instance", identity.value());
    }
}
