package com.teamproject.task.presentation;

import com.teamproject.task.application.TaskChecklistService;
import com.teamproject.task.application.dto.ChecklistDtos.ChecklistItemResponse;
import com.teamproject.task.application.dto.ChecklistDtos.ChecklistResponse;
import com.teamproject.task.application.dto.ChecklistDtos.CreateChecklistItemRequest;
import com.teamproject.task.application.dto.ChecklistDtos.UpdateChecklistItemRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TaskChecklistController {
    private final TaskChecklistService checklists;
    public TaskChecklistController(TaskChecklistService checklists) { this.checklists = checklists; }

    @GetMapping("/tasks/{taskId}/checklist-items")
    ChecklistResponse list(Authentication authentication, @PathVariable Long taskId) {
        return checklists.list((Long) authentication.getPrincipal(), taskId);
    }

    @PostMapping("/tasks/{taskId}/checklist-items")
    @ResponseStatus(HttpStatus.CREATED)
    ChecklistItemResponse create(Authentication authentication, @PathVariable Long taskId,
            @Valid @RequestBody CreateChecklistItemRequest request) {
        return checklists.create((Long) authentication.getPrincipal(), taskId, request);
    }

    @PatchMapping("/checklist-items/{itemId}")
    ChecklistItemResponse update(Authentication authentication, @PathVariable Long itemId,
            @Valid @RequestBody UpdateChecklistItemRequest request) {
        return checklists.update((Long) authentication.getPrincipal(), itemId, request);
    }

    @DeleteMapping("/checklist-items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication authentication, @PathVariable Long itemId,
            @RequestParam Long expectedVersion) {
        checklists.delete((Long) authentication.getPrincipal(), itemId, expectedVersion);
    }
}
