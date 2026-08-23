package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminLoginHistoryService;
import com.teamproject.admin.application.dto.AdminDtos.AdminLoginHistoryResponse;
import com.teamproject.admin.application.dto.AdminDtos.PageResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/login-history")
public class AdminLoginHistoryController {
    private final AdminLoginHistoryService history;
    public AdminLoginHistoryController(AdminLoginHistoryService history) { this.history = history; }

    @GetMapping
    PageResponse<AdminLoginHistoryResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return history.list(page, size);
    }
}
