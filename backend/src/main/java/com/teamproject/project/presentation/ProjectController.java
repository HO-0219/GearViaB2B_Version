package com.teamproject.project.presentation;

import com.teamproject.project.application.ProjectService;
import com.teamproject.project.application.dto.ProjectDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProjectController {
    private final ProjectService projects;
    public ProjectController(ProjectService projects) { this.projects = projects; }

    @GetMapping("/groups/{groupId}/projects")
    List<ProjectResponse> list(Authentication auth, @PathVariable Long groupId) {
        return projects.list((Long) auth.getPrincipal(), groupId);
    }

    @PostMapping("/groups/{groupId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    ProjectResponse create(Authentication auth, @PathVariable Long groupId,
            @Valid @RequestBody CreateProjectRequest request) {
        return projects.create((Long) auth.getPrincipal(), groupId, request);
    }

    @GetMapping("/projects/{projectId}")
    ProjectResponse get(Authentication auth, @PathVariable Long projectId) {
        return projects.get((Long) auth.getPrincipal(), projectId);
    }

    @PutMapping("/projects/{projectId}")
    ProjectResponse update(Authentication auth, @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return projects.update((Long) auth.getPrincipal(), projectId, request);
    }

    @DeleteMapping("/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(Authentication auth, @PathVariable Long projectId,
            @RequestParam long expectedVersion) {
        projects.archive((Long) auth.getPrincipal(), projectId, expectedVersion);
    }
}
