package com.teamproject.project.presentation;

import com.teamproject.project.application.ProjectDocumentService;
import com.teamproject.project.application.dto.ProjectDocumentDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1")
public class ProjectDocumentController {
    private final ProjectDocumentService documents;
    public ProjectDocumentController(ProjectDocumentService documents) { this.documents = documents; }

    @GetMapping("/projects/{projectId}/documents")
    ProjectFileTreeResponse list(Authentication auth, @PathVariable Long projectId,
            @RequestParam(required = false) Long issueNodeId) {
        return documents.list((Long) auth.getPrincipal(), projectId, issueNodeId);
    }
    @PostMapping("/projects/{projectId}/documents/links")
    @ResponseStatus(HttpStatus.CREATED)
    ProjectDocumentResponse link(Authentication auth, @PathVariable Long projectId,
            @Valid @RequestBody CreateProjectLinkRequest request) {
        return documents.createLink((Long) auth.getPrincipal(), projectId, request);
    }
    @PostMapping(path = "/projects/{projectId}/documents/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ProjectDocumentResponse upload(Authentication auth, @PathVariable Long projectId,
            @RequestParam(required = false) Long issueNodeId,
            @RequestParam(required = false) String title, @RequestPart MultipartFile file) {
        return documents.upload((Long) auth.getPrincipal(), projectId, issueNodeId, title, file);
    }
    @GetMapping("/project-documents/{documentId}/download")
    ResponseEntity<byte[]> download(Authentication auth, @PathVariable Long documentId) {
        var value = documents.download((Long) auth.getPrincipal(), documentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(value.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(value.content());
    }
    @DeleteMapping("/project-documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication auth, @PathVariable Long documentId) {
        documents.delete((Long) auth.getPrincipal(), documentId);
    }
}
