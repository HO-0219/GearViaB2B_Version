package com.teamproject.task.presentation;

import com.teamproject.task.application.TaskAssigneeChangeService;
import com.teamproject.task.application.dto.TaskAssigneeChangeDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TaskAssigneeChangeController {
    private final TaskAssigneeChangeService service;
    public TaskAssigneeChangeController(TaskAssigneeChangeService service) { this.service = service; }
    @PostMapping("/tasks/{taskId}/assignee-change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    Response create(Authentication authentication, @PathVariable Long taskId, @Valid @RequestBody CreateRequest request) {
        return service.create((Long) authentication.getPrincipal(), taskId, request);
    }
    @GetMapping("/groups/{groupId}/assignee-change-requests")
    List<Response> list(Authentication authentication, @PathVariable Long groupId) {
        return service.list((Long) authentication.getPrincipal(), groupId);
    }
    @PostMapping("/task-assignee-change-requests/{requestId}/decision")
    Response decide(Authentication authentication, @PathVariable Long requestId, @Valid @RequestBody DecisionRequest request) {
        return service.decide((Long) authentication.getPrincipal(), requestId, request);
    }
}
