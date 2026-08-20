package com.teamproject.project.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.common.storage.GroupStorageQuotaService;
import com.teamproject.group.application.*;
import com.teamproject.group.domain.*;
import com.teamproject.project.application.dto.ProjectDocumentDtos.*;
import com.teamproject.project.domain.*;
import com.teamproject.resource.storage.FileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ProjectDocumentService {
    private static final Set<String> EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "gif", "txt", "csv", "docx", "xlsx", "pptx", "zip");
    private final ProjectRepository projects;
    private final ProjectIssueRepository issues;
    private final ProjectDocumentRepository documents;
    private final GroupStorageQuotaService quota;
    private final GroupAuthorization authorization;
    private final GroupFeaturePolicy features;
    private final FileStorage storage;

    public ProjectDocumentService(ProjectRepository projects, ProjectIssueRepository issues,
            ProjectDocumentRepository documents, GroupAuthorization authorization, GroupStorageQuotaService quota,
            GroupFeaturePolicy features, FileStorage storage) {
        this.projects = projects; this.issues = issues; this.documents = documents; this.authorization = authorization;
        this.features = features; this.storage = storage; this.quota = quota;
    }

    @Transactional(readOnly = true)
    public ProjectFileTreeResponse list(Long userId, Long projectId, Long issueNodeId) {
        Project project = project(projectId);
        authorization.requireActiveMember(project.getGroup().getId(), userId);
        if (issueNodeId != null) location(project, issueNodeId, true);
        var policy = features.policy(userId, project.getGroup().getId());
        var usage = quota.usage(userId, project.getGroup().getId());
        return new ProjectFileTreeResponse(projectId, usage.usedBytes(), usage.limitBytes(), usage.remainingBytes(),
                documents.findAllByProjectIdAndIssueNodeIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
                        .stream().map(value -> response(value, userId)).toList(),
                issueNodeId == null ? List.of()
                        : documents.findAllByProjectIdAndIssueNodeIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId, issueNodeId)
                                .stream().map(value -> response(value, userId)).toList());
    }

    @Transactional
    public ProjectDocumentResponse createLink(Long userId, Long projectId, CreateProjectLinkRequest request) {
        Project project = project(projectId); GroupMember actor = member(project, userId); requireMutable(project);
        ProjectIssue location = location(project, request.issueNodeId(), false);
        ProjectDocument saved = documents.save(ProjectDocument.link(project, location, actor,
                request.title().trim(), safeHttpsUrl(request.url())));
        return response(saved, userId);
    }

    @Transactional
    public ProjectDocumentResponse upload(Long userId, Long projectId, Long issueNodeId,
            String title, MultipartFile file) {
        Project project = project(projectId); GroupMember actor = member(project, userId); requireMutable(project);
        ProjectIssue location = location(project, issueNodeId, false);
        var policy = features.policy(userId, project.getGroup().getId());
        byte[] bytes = validate(file, policy.attachmentLimitBytes());
        // Serialize quota checks per group so concurrent uploads cannot both consume the same remaining bytes.
        quota.requireCapacity(userId, project.getGroup().getId(), bytes.length);
        String checksum = sha256(bytes);
        boolean duplicate = issueNodeId == null
                ? documents.existsByProjectIdAndIssueNodeIsNullAndChecksumSha256AndDeletedAtIsNull(projectId, checksum)
                : documents.existsByProjectIdAndIssueNodeIdAndChecksumSha256AndDeletedAtIsNull(projectId, issueNodeId, checksum);
        if (duplicate) throw new ApplicationException("PROJECT_DOCUMENT_DUPLICATE", HttpStatus.CONFLICT,
                "이 위치에 같은 파일이 이미 등록되어 있습니다.");
        String filename = safeFilename(file.getOriginalFilename()); String extension = extension(filename);
        String key = "groups/" + project.getGroup().getId() + "/projects/" + projectId + "/documents/"
                + (issueNodeId == null ? "root" : issueNodeId) + "/" + UUID.randomUUID() + "." + extension;
        String contentType = contentType(extension); storage.put(key, bytes, contentType); deleteOnRollback(key);
        ProjectDocument saved = documents.saveAndFlush(ProjectDocument.file(project, location, actor,
                title == null || title.isBlank() ? filename : title.trim(), key, filename, contentType, bytes.length, checksum));
        return response(saved, userId);
    }

    @Transactional(readOnly = true)
    public Download download(Long userId, Long documentId) {
        ProjectDocument value = document(documentId);
        authorization.requireActiveMember(value.getProject().getGroup().getId(), userId);
        if (value.getDocumentType() != ProjectDocument.Type.FILE) throw new ApplicationException(
                "PROJECT_DOCUMENT_NOT_FILE", HttpStatus.BAD_REQUEST, "다운로드할 파일이 아닙니다.");
        var file = storage.get(value.getStorageKey());
        return new Download(file.content(), value.getContentType(), value.getOriginalFilename());
    }

    @Transactional
    public void delete(Long userId, Long documentId) {
        ProjectDocument value = document(documentId); GroupMember actor = member(value.getProject(), userId);
        if (!value.getCreatedBy().getId().equals(actor.getId()) && actor.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("PROJECT_DOCUMENT_DELETE_FORBIDDEN", HttpStatus.FORBIDDEN,
                    "파일을 삭제할 권한이 없습니다.");
        }
        value.delete(); if (value.getStorageKey() != null) deleteAfterCommit(value.getStorageKey());
    }

    private Project project(Long id) { return projects.findById(id).orElseThrow(() -> new ApplicationException(
            "PROJECT_NOT_FOUND", HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")); }
    private ProjectDocument document(Long id) { return documents.findByIdAndDeletedAtIsNull(id).orElseThrow(() ->
            new ApplicationException("PROJECT_DOCUMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다.")); }
    private GroupMember member(Project project, Long userId) {
        return authorization.requireActiveMember(project.getGroup().getId(), userId);
    }
    private ProjectIssue location(Project project, Long nodeId, boolean includeArchived) {
        if (nodeId == null) return null;
        ProjectIssue value = (includeArchived ? issues.findById(nodeId) : issues.findByIdAndArchivedAtIsNull(nodeId))
                .orElseThrow(() -> new ApplicationException(
                "PROJECT_DOCUMENT_LOCATION_NOT_FOUND", HttpStatus.NOT_FOUND, "파일 위치를 찾을 수 없습니다."));
        if (!value.getProject().getId().equals(project.getId())) throw new ApplicationException(
                "PROJECT_DOCUMENT_LOCATION_NOT_FOUND", HttpStatus.NOT_FOUND, "파일 위치를 찾을 수 없습니다.");
        return value;
    }
    private void requireMutable(Project project) { if (project.getStatus() == Project.Status.ARCHIVED)
        throw new ApplicationException("PROJECT_ARCHIVED", HttpStatus.CONFLICT, "보관된 프로젝트는 변경할 수 없습니다."); }
    private String safeHttpsUrl(String raw) { try {
        URI uri = URI.create(raw.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException();
        return uri.toASCIIString();
    } catch (RuntimeException exception) { throw new ApplicationException(
            "PROJECT_DOCUMENT_URL_INVALID", HttpStatus.BAD_REQUEST, "올바른 HTTPS 링크를 입력해 주세요."); } }
    private byte[] validate(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty() || file.getSize() > maxBytes) throw invalid(
                "관리자 정책의 파일당 업로드 한도를 확인해 주세요.");
        String filename = safeFilename(file.getOriginalFilename()); String extension = extension(filename);
        if (!EXTENSIONS.contains(extension)) throw invalid("허용되지 않는 파일 형식입니다.");
        try { byte[] bytes = file.getBytes(); if (!matchesSignature(extension, bytes))
            throw invalid("파일 확장자와 실제 형식이 일치하지 않습니다."); return bytes;
        } catch (java.io.IOException exception) { throw invalid("파일을 읽을 수 없습니다."); }
    }
    private boolean matchesSignature(String extension, byte[] bytes) {
        if (Set.of("txt", "csv").contains(extension)) { for (byte value : bytes) if (value == 0) return false; return true; }
        if ("pdf".equals(extension)) return starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
        if (Set.of("docx", "xlsx", "pptx", "zip").contains(extension)) return starts(bytes, new byte[]{0x50, 0x4b});
        if ("png".equals(extension)) return starts(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        if (Set.of("jpg", "jpeg").contains(extension)) return starts(bytes, new byte[]{(byte) 0xff, (byte) 0xd8});
        return "gif".equals(extension) && starts(bytes, "GIF8".getBytes(StandardCharsets.US_ASCII));
    }
    private boolean starts(byte[] value, byte[] prefix) { if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false; return true; }
    private String safeFilename(String raw) { String value = raw == null ? "file" : raw.replace("\\", "/");
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\\"]", "_").trim();
        if (value.isBlank() || value.length() > 255) throw invalid("올바른 파일 이름이 아닙니다."); return value; }
    private String extension(String filename) { int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private String contentType(String extension) { return switch (extension) {
        case "pdf" -> "application/pdf"; case "png" -> "image/png"; case "jpg", "jpeg" -> "image/jpeg";
        case "gif" -> "image/gif"; case "txt" -> "text/plain;charset=UTF-8"; case "csv" -> "text/csv;charset=UTF-8";
        case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        default -> "application/zip"; }; }
    private String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private ProjectDocumentResponse response(ProjectDocument value, Long userId) {
        GroupMember viewer = authorization.requireActiveMember(value.getProject().getGroup().getId(), userId);
        return new ProjectDocumentResponse(value.getId(), value.getProject().getId(),
                value.getIssueNode() == null ? null : value.getIssueNode().getId(), value.getDocumentType().name(),
                value.getTitle(), value.getExternalUrl(), value.getOriginalFilename(), value.getContentType(),
                value.getSizeBytes(), value.getCreatedBy().getId(), value.getCreatedBy().getUser().getNickname(),
                value.getCreatedAt(), viewer.getRole() == GroupMember.Role.LEADER || value.getCreatedBy().getId().equals(viewer.getId()));
    }
    private ApplicationException invalid(String message) { return new ApplicationException(
            "PROJECT_DOCUMENT_FILE_INVALID", HttpStatus.BAD_REQUEST, message); }
    private void deleteOnRollback(String key) { if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) storage.delete(key); }}); }
    private void deleteAfterCommit(String key) { if (!TransactionSynchronizationManager.isSynchronizationActive()) { storage.delete(key); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { storage.delete(key); }}); }
    public record Download(byte[] content, String contentType, String filename) {}
}
