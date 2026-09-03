package com.teamproject.deployment.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically finalizes domain/TLS apply jobs that were left in {@code SWITCHED}
 * because the administrator's browser stopped polling before the asynchronous
 * host applier reported its result.
 */
@Component
public class DeploymentApplyReconciler {

    private final DeploymentSettingsService service;

    public DeploymentApplyReconciler(DeploymentSettingsService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.host-apply.reconcile-ms:30000}")
    public void reconcile() {
        service.reconcilePendingApplies();
    }
}
