package com.teamproject.admin.application;

import com.teamproject.resource.storage.DynamicFileStorage;
import org.springframework.stereotype.Service;

/**
 * Lets an admin check NAS/company-storage reachability from the web and, on a
 * successful test, switch the live storage provider without restarting the
 * container — falling back to (or staying on) local disk if the test fails.
 */
@Service
public class AdminStorageSettingsService {
    private final DynamicFileStorage storage;

    public AdminStorageSettingsService(DynamicFileStorage storage) {
        this.storage = storage;
    }

    public DynamicFileStorage.Status status() {
        return storage.status();
    }

    public DynamicFileStorage.TestResult testNas() {
        return storage.testNas();
    }

    public DynamicFileStorage.TestResult activateNas() {
        return storage.activateNas();
    }

    public com.teamproject.resource.storage.NasMigrationService.NasPreflight preflightNas() {
        return storage.preflightNas();
    }

    public com.teamproject.resource.storage.NasMigrationService.MigrationResult migrateToNas() {
        return storage.migrateToNas();
    }

    public com.teamproject.resource.storage.NasMigrationService.MigrationResult rollbackToLocal() {
        return storage.rollbackToLocal();
    }

    public void activateLocal() {
        storage.activateLocal();
    }
}
