package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminService;
import com.teamproject.admin.application.dto.AdminDtos.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService admin;
    private final com.teamproject.admin.application.AdminAuditService audit;
    public AdminController(AdminService admin, com.teamproject.admin.application.AdminAuditService audit) {
        this.admin = admin; this.audit = audit;
    }
    @GetMapping("/overview") OverviewResponse overview() { return admin.overview(); }
    @GetMapping("/users")
    PageResponse<AdminUserResponse> users(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) { return admin.users(page, size); }
    @PatchMapping("/users/{userId}/status")
    AdminUserResponse status(Authentication auth, @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        return admin.changeStatus((Long) auth.getPrincipal(), userId, request.getOrDefault("status", ""));
    }
    @PostMapping("/users") TemporaryPasswordResponse create(@jakarta.validation.Valid @RequestBody CreateUserRequest request) { return admin.createUser(request); }
    @PostMapping("/users/{userId}/temporary-password") TemporaryPasswordResponse reset(@PathVariable Long userId) { return admin.resetPassword(userId); }
    @PostMapping("/users/{userId}/end-sessions") void endSessions(@PathVariable Long userId) { admin.endSessions(userId); }
    @GetMapping("/groups")
    PageResponse<AdminGroupResponse> groups(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return admin.groups(page, size); }
    @GetMapping("/report-downloads")
    PageResponse<AdminReportDownloadResponse> reportDownloads(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return admin.reportDownloads(page, size); }
    @GetMapping("/report-deliveries")
    PageResponse<AdminReportDeliveryResponse> reportDeliveries(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return admin.reportDeliveries(page, size); }
    @GetMapping("/audit-logs")
    PageResponse<AdminAuditResponse> auditLogs(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return audit.list(page, size);
    }
}
