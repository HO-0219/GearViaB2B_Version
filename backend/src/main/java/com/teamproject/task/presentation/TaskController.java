package com.teamproject.task.presentation;

import com.teamproject.task.application.TaskService;
import com.teamproject.task.application.dto.TaskDtos.CreateTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.AssignTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.ClaimTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.TaskHistoryResponse;
import com.teamproject.task.application.dto.TaskDtos.TaskResponse;
import com.teamproject.task.application.dto.TaskDtos.TransitionTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.UpdateTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.LinkProjectRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TaskController {
    private final TaskService tasks;
    public TaskController(TaskService tasks) { this.tasks = tasks; }

    @GetMapping("/groups/{groupId}/tasks")
    List<TaskResponse> list(Authentication authentication, @PathVariable Long groupId) {
        return tasks.list((Long) authentication.getPrincipal(), groupId);
    }

    @PostMapping("/groups/{groupId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    TaskResponse create(Authentication authentication, @PathVariable Long groupId,
            @Valid @RequestBody CreateTaskRequest request) {
        return tasks.create((Long) authentication.getPrincipal(), groupId, request);
    }

    @GetMapping("/tasks/{taskId}")
    TaskResponse get(Authentication authentication, @PathVariable Long taskId) {
        return tasks.get((Long) authentication.getPrincipal(), taskId);
    }

    @PatchMapping("/tasks/{taskId}")
    TaskResponse update(Authentication authentication, @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        return tasks.update((Long) authentication.getPrincipal(), taskId, request);
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication authentication, @PathVariable Long taskId,
            @RequestParam long expectedVersion) {
        tasks.delete((Long) authentication.getPrincipal(), taskId, expectedVersion);
    }

    @PatchMapping("/tasks/{taskId}/project-link")
    TaskResponse linkProject(Authentication authentication, @PathVariable Long taskId,
            @Valid @RequestBody LinkProjectRequest request) {
        return tasks.linkProject((Long) authentication.getPrincipal(), taskId, request);
    }

    @PostMapping("/tasks/{taskId}/transitions")
    TaskResponse transition(Authentication authentication, @PathVariable Long taskId,
            @Valid @RequestBody TransitionTaskRequest request) {
        return tasks.transition((Long) authentication.getPrincipal(), taskId, request);
    }

    @PutMapping("/tasks/{taskId}/assignee")
    TaskResponse assign(Authentication authentication, @PathVariable Long taskId,
            @Valid @RequestBody AssignTaskRequest request) {
        return tasks.assign((Long) authentication.getPrincipal(), taskId, request);
    }

    @PutMapping("/tasks/{taskId}/assignee/me")
    TaskResponse claim(Authentication authentication, @PathVariable Long taskId,
            @Valid @RequestBody ClaimTaskRequest request) {
        return tasks.claim((Long) authentication.getPrincipal(), taskId, request);
    }

    @GetMapping("/tasks/{taskId}/histories")
    List<TaskHistoryResponse> histories(Authentication authentication, @PathVariable Long taskId) {
        return tasks.histories((Long) authentication.getPrincipal(), taskId);
    }
}
