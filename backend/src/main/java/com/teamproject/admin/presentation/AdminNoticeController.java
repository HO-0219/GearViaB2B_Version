package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminNoticeService;
import com.teamproject.admin.application.dto.AdminDtos.AdminNoticeResponse;
import com.teamproject.admin.application.dto.AdminDtos.CreateAdminNoticeRequest;
import com.teamproject.admin.application.dto.AdminDtos.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notices")
public class AdminNoticeController {
    private final AdminNoticeService notices;
    public AdminNoticeController(AdminNoticeService notices) { this.notices = notices; }

    @GetMapping
    PageResponse<AdminNoticeResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return notices.list(page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdminNoticeResponse create(Authentication auth, @Valid @RequestBody CreateAdminNoticeRequest request) {
        return notices.create((Long) auth.getPrincipal(), request);
    }

    @DeleteMapping("/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable Long noticeId) {
        notices.cancel(noticeId);
    }
}
