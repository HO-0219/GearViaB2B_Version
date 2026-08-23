package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.AdminLoginHistoryResponse;
import com.teamproject.admin.application.dto.AdminDtos.PageResponse;
import com.teamproject.authentication.domain.LoginHistory;
import com.teamproject.authentication.domain.LoginHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminLoginHistoryService {
    private final LoginHistoryRepository history;
    public AdminLoginHistoryService(LoginHistoryRepository history) { this.history = history; }

    @Transactional(readOnly = true)
    public PageResponse<AdminLoginHistoryResponse> list(int page, int size) {
        var result = history.findAllByOrderByOccurredAtDescIdDesc(
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return new PageResponse<>(result.map(this::response).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private AdminLoginHistoryResponse response(LoginHistory value) {
        return new AdminLoginHistoryResponse(value.getId(), value.getUsername(),
                value.getUser() == null ? null : value.getUser().getId(), value.getOutcome().name(),
                value.getIpAddress(), value.getDeviceName(), value.getOccurredAt());
    }
}
