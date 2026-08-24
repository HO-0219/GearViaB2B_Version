package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminAiSettingsService;
import com.teamproject.admin.application.AdminAiSettingsService.ConnectionTestResponse;
import com.teamproject.admin.application.AdminAiSettingsService.StatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai-settings")
public class AdminAiSettingsController {
    private final AdminAiSettingsService service;

    public AdminAiSettingsController(AdminAiSettingsService service) {
        this.service = service;
    }

    @GetMapping
    StatusResponse status() {
        return service.status();
    }

    @PostMapping("/test")
    ConnectionTestResponse test() {
        return service.testConnections();
    }
}
