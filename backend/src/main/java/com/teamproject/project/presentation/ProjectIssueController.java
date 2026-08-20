package com.teamproject.project.presentation;

import com.teamproject.project.application.ProjectIssueService;
import com.teamproject.project.application.dto.ProjectIssueDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProjectIssueController {
    private final ProjectIssueService issues;
    public ProjectIssueController(ProjectIssueService issues) { this.issues = issues; }

    @GetMapping("/projects/{projectId}/issues")
    List<IssueNodeResponse> list(Authentication auth, @PathVariable Long projectId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return issues.list((Long) auth.getPrincipal(), projectId, includeArchived);
    }
    @PostMapping("/projects/{projectId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    IssueNodeResponse create(Authentication auth, @PathVariable Long projectId,
            @Valid @RequestBody CreateIssueNodeRequest request) {
        return issues.create((Long) auth.getPrincipal(), projectId, request);
    }
    @PutMapping("/project-issues/{issueId}")
    IssueNodeResponse update(Authentication auth, @PathVariable Long issueId,
            @Valid @RequestBody UpdateIssueNodeRequest request) {
        return issues.update((Long) auth.getPrincipal(), issueId, request);
    }
    @DeleteMapping("/project-issues/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(Authentication auth, @PathVariable Long issueId, @RequestParam long expectedVersion) {
        issues.archive((Long) auth.getPrincipal(), issueId, expectedVersion);
    }

    @PostMapping("/project-issues/{issueId}/checklist")
    @ResponseStatus(HttpStatus.CREATED)
    IssueChecklistResponse createChecklist(Authentication auth, @PathVariable Long issueId,
            @Valid @RequestBody CreateIssueChecklistRequest request) {
        return issues.createChecklist((Long) auth.getPrincipal(), issueId, request);
    }
    @PutMapping("/project-issue-checklist/{itemId}")
    IssueChecklistResponse updateChecklist(Authentication auth, @PathVariable Long itemId,
            @Valid @RequestBody UpdateIssueChecklistRequest request) {
        return issues.updateChecklist((Long) auth.getPrincipal(), itemId, request);
    }
    @DeleteMapping("/project-issue-checklist/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteChecklist(Authentication auth, @PathVariable Long itemId, @RequestParam long expectedVersion) {
        issues.deleteChecklist((Long) auth.getPrincipal(), itemId, expectedVersion);
    }

    @PostMapping(path = "/project-issues/{issueId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    IssueImageResponse uploadImage(Authentication auth, @PathVariable Long issueId,
            @RequestParam(required = false) Integer sortOrder, @RequestPart MultipartFile file) {
        return issues.uploadImage((Long) auth.getPrincipal(), issueId, sortOrder, file);
    }
    @GetMapping("/project-issue-images/{imageId}/content")
    ResponseEntity<byte[]> image(Authentication auth, @PathVariable Long imageId) {
        var value = issues.downloadImage((Long) auth.getPrincipal(), imageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(value.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(value.content());
    }
    @DeleteMapping("/project-issue-images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteImage(Authentication auth, @PathVariable Long imageId) {
        issues.deleteImage((Long) auth.getPrincipal(), imageId);
    }
}
