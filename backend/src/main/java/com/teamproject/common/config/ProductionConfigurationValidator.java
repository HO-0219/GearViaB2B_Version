package com.teamproject.common.config;

import org.springframework.core.env.Environment;

/**
 * Compatibility facade for code that still refers to the old validator name.
 * B2B deployment safety rules live in {@link B2bConfigurationValidator}.
 */
public class ProductionConfigurationValidator {
    private final B2bConfigurationValidator delegate;

    public ProductionConfigurationValidator(Environment environment) {
        this.delegate = new B2bConfigurationValidator(environment);
    }

    void validate() {
        delegate.validate();
    }
}
