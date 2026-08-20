package com.teamproject.project.application.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectDocumentDtos {
    private ProjectDocumentDtos() {}
    public record CreateProjectLinkRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 1000) String url,
            Long issueNodeId) {}
    public record ProjectDocumentResponse(
            Long id, Long projectId, Long issueNodeId, String type, String title, String url,
            String originalFilename, String contentType, Long sizeBytes,
            Long createdByMemberId, String createdByNickname, LocalDateTime createdAt, boolean canDelete) {}
    public record ProjectFileTreeResponse(
            Long projectId, long usedBytes, long limitBytes, long remainingBytes,
            List<ProjectDocumentResponse> rootDocuments, List<ProjectDocumentResponse> nodeDocuments) {}
}
