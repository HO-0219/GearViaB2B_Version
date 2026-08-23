package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminStorageSettingsService;
import com.teamproject.admin.application.AdminStorageSettingsService.StatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
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
    StatusResponse status() {
        return service.status();
    }
}
