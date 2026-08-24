package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminStorageSettingsService;
import com.teamproject.resource.storage.DynamicFileStorage.Status;
import com.teamproject.resource.storage.DynamicFileStorage.TestResult;
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

    @PostMapping("/local/activate")
    Status activateLocal() {
        service.activateLocal();
        return service.status();
    }
}
