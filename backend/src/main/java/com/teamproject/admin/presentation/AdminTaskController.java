package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminTaskService;
import com.teamproject.admin.application.dto.AdminDtos.AdminTaskResponse;
import com.teamproject.admin.application.dto.AdminDtos.PageResponse;
import com.teamproject.admin.application.dto.AdminDtos.SuspendTaskRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tasks")
public class AdminTaskController {
    private final AdminTaskService tasks;
    public AdminTaskController(AdminTaskService tasks) { this.tasks = tasks; }

    @GetMapping
    PageResponse<AdminTaskResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return tasks.list(page, size);
    }

    @GetMapping("/deleted")
    List<AdminTaskResponse> deleted() {
        return tasks.recentlyDeleted();
    }

    @PostMapping("/{taskId}/suspend")
    AdminTaskResponse suspend(@PathVariable Long taskId, @Valid @RequestBody SuspendTaskRequest request) {
        return tasks.suspend(taskId, request.reason().trim());
    }

    @PostMapping("/{taskId}/resume")
    AdminTaskResponse resume(@PathVariable Long taskId) {
        return tasks.resume(taskId);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long taskId) {
        tasks.delete(taskId);
    }

    @PostMapping("/{taskId}/restore")
    AdminTaskResponse restore(@PathVariable Long taskId) {
        return tasks.restore(taskId);
    }
}
