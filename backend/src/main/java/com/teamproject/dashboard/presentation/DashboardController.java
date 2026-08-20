package com.teamproject.dashboard.presentation;

import com.teamproject.dashboard.application.DashboardService;
import com.teamproject.dashboard.application.dto.DashboardDtos.GroupDashboardResponse;
import com.teamproject.dashboard.application.dto.DashboardDtos.PersonalDashboardResponse;
import com.teamproject.dashboard.application.dto.DashboardDtos.MemberReportResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {
    private final DashboardService dashboards;
    public DashboardController(DashboardService dashboards) { this.dashboards = dashboards; }

    @GetMapping("/dashboard/me")
    PersonalDashboardResponse personal(Authentication authentication) {
        return dashboards.personal((Long) authentication.getPrincipal());
    }

    @GetMapping("/groups/{groupId}/dashboard")
    GroupDashboardResponse group(Authentication authentication, @PathVariable Long groupId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return dashboards.group((Long) authentication.getPrincipal(), groupId, from, to);
    }

    @GetMapping("/groups/{groupId}/reports/me")
    MemberReportResponse memberReport(Authentication authentication, @PathVariable Long groupId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return dashboards.memberReport((Long) authentication.getPrincipal(), groupId, from, to);
    }
}
