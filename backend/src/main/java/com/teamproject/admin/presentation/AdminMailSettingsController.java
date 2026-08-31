package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminMailSettingsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/mail-settings")
public class AdminMailSettingsController {
    private final AdminMailSettingsService service;
    public AdminMailSettingsController(AdminMailSettingsService service) { this.service = service; }
    @GetMapping AdminMailSettingsService.StatusResponse status() { return service.status(); }
    @PutMapping AdminMailSettingsService.StatusResponse update(@RequestBody AdminMailSettingsService.UpdateRequest request) { return service.update(request); }
    @PostMapping("/test") AdminMailSettingsService.TestResponse test(@RequestBody AdminMailSettingsService.TestRequest request) { return service.test(request); }
}
