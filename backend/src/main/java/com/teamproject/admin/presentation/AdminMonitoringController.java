package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminMonitoringService;
import com.teamproject.admin.application.dto.AdminDtos.AdminMonitoringResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/monitoring")
public class AdminMonitoringController {
    private final AdminMonitoringService monitoring;

    public AdminMonitoringController(AdminMonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @GetMapping
    AdminMonitoringResponse overview() {
        return monitoring.overview();
    }
}
