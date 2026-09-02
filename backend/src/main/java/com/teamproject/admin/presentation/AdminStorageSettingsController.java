package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminStorageSettingsService;
import com.teamproject.resource.storage.DynamicFileStorage.Status;
import com.teamproject.resource.storage.DynamicFileStorage.TestResult;
import com.teamproject.resource.storage.NasMigrationService.MigrationResult;
import com.teamproject.resource.storage.NasMigrationService.NasPreflight;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/storage-settings")
public class AdminStorageSettingsController {
    private final AdminStorageSettingsService service;

    public AdminStorageSettingsController(AdminStorageSettingsService service) {
        this.service = service;
    }

    @GetMapping
    Status status() {
        return service.status();
    }

    @PostMapping("/nas/test")
    TestResult testNas() {
        return service.testNas();
    }

    @PostMapping("/nas/activate")
    TestResult activateNas() {
        return service.activateNas();
    }

    @PostMapping("/nas/preflight")
    NasPreflight preflightNas() {
        return service.preflightNas();
    }

    @PostMapping("/nas/migrate")
    MigrationResult migrateToNas() {
        return service.migrateToNas();
    }

    @PostMapping("/nas/rollback")
    MigrationResult rollbackToLocal() {
        return service.rollbackToLocal();
    }

    @PostMapping("/local/activate")
    Status activateLocal() {
        service.activateLocal();
        return service.status();
    }
}
