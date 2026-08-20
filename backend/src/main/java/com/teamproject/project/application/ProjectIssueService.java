package com.teamproject.project.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.common.storage.GroupStorageQuotaService;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.application.GroupFeaturePolicy;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.project.application.dto.ProjectIssueDtos.*;
import com.teamproject.project.domain.*;
import com.teamproject.resource.storage.FileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProjectIssueService {
    private static final int MAX_NODES_PER_PARENT = 100;
    private static final int MAX_NODES_PER_PROJECT = 10_000;
    private static final int MAX_CHECKLIST_ITEMS = 100;
    private static final int MAX_IMAGES = 12;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> IMAGE_FORMATS = Set.of("jpeg", "jpg", "png", "gif");
    private final ProjectRepository projects;
    private final ProjectIssueRepository issues;
    private final ProjectIssueChecklistRepository checklist;
    private final ProjectIssueImageRepository images;
    private final GroupAuthorization authorization;
    private final GroupFeaturePolicy features;
    private final FileStorage storage;
    private final GroupStorageQuotaService quota;

    public ProjectIssueService(ProjectRepository projects, ProjectIssueRepository issues,
            ProjectIssueChecklistRepository checklist, ProjectIssueImageRepository images,
            GroupAuthorization authorization, GroupFeaturePolicy features, FileStorage storage,
            GroupStorageQuotaService quota) {
        this.projects = projects; this.issues = issues; this.checklist = checklist; this.images = images;
        this.authorization = authorization; this.features = features; this.storage = storage; this.quota = quota;
    }

    @Transactional(readOnly = true)
    public List<IssueNodeResponse> list(Long userId, Long projectId, boolean includeArchived) {
        Project project = project(projectId);
        GroupMember viewer = authorization.requireActiveMember(project.getGroup().getId(), userId);
        List<ProjectIssue> values = includeArchived
                ? issues.findAllByProjectIdOrderBySortOrderAscIdAsc(projectId)
                : issues.findAllByProjectIdAndArchivedAtIsNullOrderBySortOrderAscIdAsc(projectId);
        List<Long> leafIds = values.stream().filter(value -> value.getLevel() == ProjectIssue.Level.ISSUE)
                .map(ProjectIssue::getId).toList();
        Map<Long, List<ProjectIssueChecklistItem>> checklistByIssue = leafIds.isEmpty() ? Map.of()
                : checklist.findAllByIssueIdInOrderByIssueIdAscSortOrderAscIdAsc(leafIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(value -> value.getIssue().getId()));
        Map<Long, List<ProjectIssueImage>> imagesByIssue = leafIds.isEmpty() ? Map.of()
                : images.findAllByIssueIdInOrderByIssueIdAscSortOrderAscIdAsc(leafIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(value -> value.getIssue().getId()));
        return values.stream().map(value -> response(value, viewer,
                checklistByIssue.getOrDefault(value.getId(), List.of()),
                imagesByIssue.getOrDefault(value.getId(), List.of()))).toList();
    }

    @Transactional
    public IssueNodeResponse create(Long userId, Long projectId, CreateIssueNodeRequest request) {
        Project project = projects.findByIdForUpdate(projectId).orElseThrow(() -> notFound("프로젝트"));
        GroupMember actor = authorization.requireActiveMember(project.getGroup().getId(), userId);
        requireMutable(project);
        ProjectIssue.Level level = level(request.level());
        ProjectIssue parent = validateParent(project, level, request.parentId());
        if (level != ProjectIssue.Level.ISSUE) requireStructureManager(project, actor);
        if (issues.countByProjectIdAndArchivedAtIsNull(projectId) >= MAX_NODES_PER_PROJECT) throw new ApplicationException(
                "PROJECT_ISSUE_PROJECT_LIMIT", HttpStatus.CONFLICT,
                "한 프로젝트에는 최대 10,000개의 활성 항목을 만들 수 있습니다.");
        int siblingCount = parent == null
                ? issues.countByProjectIdAndParentIsNullAndArchivedAtIsNull(projectId)
                : issues.countByProjectIdAndParentIdAndArchivedAtIsNull(projectId, parent.getId());
        if (siblingCount >= MAX_NODES_PER_PARENT) throw new ApplicationException(
                "PROJECT_ISSUE_LIMIT", HttpStatus.CONFLICT, "한 분류에는 최대 100개의 항목을 만들 수 있습니다.");
        GroupMember assignee = optionalMember(project, request.assigneeMemberId());
        ProjectIssue saved = issues.saveAndFlush(new ProjectIssue(project, parent, assignee, actor, level,
                request.title().trim(), blankToNull(request.description()),
                request.sortOrder() == null ? siblingCount : request.sortOrder(), request.dueDate()));
        return response(saved, actor);
    }

    @Transactional
    public IssueNodeResponse update(Long userId, Long issueId, UpdateIssueNodeRequest request) {
        ProjectIssue issue = issue(issueId);
        GroupMember actor = authorization.requireActiveMember(issue.getProject().getGroup().getId(), userId);
        requireMutable(issue.getProject()); requireManage(issue, actor); requireVersion(issue, request.expectedVersion());
        GroupMember assignee = optionalMember(issue.getProject(), request.assigneeMemberId());
        ProjectIssue.Status status = issue.getLevel() == ProjectIssue.Level.ISSUE
                ? status(request.status()) : ProjectIssue.Status.OPEN;
        issue.update(request.title().trim(), blankToNull(request.description()), assignee, status,
                request.sortOrder(), request.dueDate());
        issues.flush();
        return response(issue, actor);
    }

    @Transactional
    public void archive(Long userId, Long issueId, long expectedVersion) {
        ProjectIssue issue = issue(issueId);
        GroupMember actor = authorization.requireActiveMember(issue.getProject().getGroup().getId(), userId);
        requireMutable(issue.getProject()); requireManage(issue, actor); requireVersion(issue, expectedVersion);
        archiveTree(issue);
        issues.flush();
    }

    @Transactional
    public IssueChecklistResponse createChecklist(Long userId, Long issueId, CreateIssueChecklistRequest request) {
        ProjectIssue issue = leaf(issueId);
        GroupMember actor = actor(userId, issue); requireMutable(issue.getProject()); requireManage(issue, actor);
        int count = checklist.countByIssueId(issueId);
        if (count >= MAX_CHECKLIST_ITEMS) throw new ApplicationException(
                "PROJECT_ISSUE_CHECKLIST_LIMIT", HttpStatus.CONFLICT, "체크리스트는 최대 100개까지 만들 수 있습니다.");
        var saved = checklist.saveAndFlush(new ProjectIssueChecklistItem(issue, request.content().trim(),
                request.sortOrder() == null ? count : request.sortOrder()));
        return checklistResponse(saved);
    }

    @Transactional
    public IssueChecklistResponse updateChecklist(Long userId, Long itemId, UpdateIssueChecklistRequest request) {
        ProjectIssueChecklistItem item = checklist.findById(itemId).orElseThrow(() -> notFound("체크리스트 항목"));
        ProjectIssue issue = active(item.getIssue());
        GroupMember actor = actor(userId, issue); requireMutable(issue.getProject()); requireManage(issue, actor);
        if (item.getVersion() != request.expectedVersion()) throw versionConflict("체크리스트");
        String content = request.content() == null ? null : request.content().trim();
        if (content != null && content.isBlank()) throw new ApplicationException(
                "PROJECT_ISSUE_CHECKLIST_CONTENT_REQUIRED", HttpStatus.BAD_REQUEST,
                "체크리스트 내용을 입력해 주세요.");
        item.update(content, request.completed(), request.sortOrder(), actor);
        checklist.flush();
        return checklistResponse(item);
    }

    @Transactional
    public void deleteChecklist(Long userId, Long itemId, long expectedVersion) {
        ProjectIssueChecklistItem item = checklist.findById(itemId).orElseThrow(() -> notFound("체크리스트 항목"));
        ProjectIssue issue = active(item.getIssue());
        GroupMember actor = actor(userId, issue); requireMutable(issue.getProject()); requireManage(issue, actor);
        if (item.getVersion() != expectedVersion) throw versionConflict("체크리스트");
        checklist.delete(item); checklist.flush();
    }

    @Transactional
    public IssueImageResponse uploadImage(Long userId, Long issueId, Integer sortOrder, MultipartFile file) {
        ProjectIssue issue = leaf(issueId);
        GroupMember actor = actor(userId, issue); requireMutable(issue.getProject()); requireManage(issue, actor);
        if (images.countByIssueId(issueId) >= MAX_IMAGES) throw new ApplicationException(
                "PROJECT_ISSUE_IMAGE_LIMIT", HttpStatus.CONFLICT, "상세 이미지는 최대 12장까지 등록할 수 있습니다.");
        long planLimit = features.policy(userId, issue.getProject().getGroup().getId()).attachmentLimitBytes();
        ImageValue image = validateImage(file, Math.min(MAX_IMAGE_BYTES, planLimit));
        quota.requireCapacity(userId, issue.getProject().getGroup().getId(), image.bytes().length);
        if (images.existsByIssueIdAndChecksumSha256(issueId, image.checksum())) throw new ApplicationException(
                "PROJECT_ISSUE_IMAGE_DUPLICATE", HttpStatus.CONFLICT, "같은 이미지가 이미 등록되어 있습니다.");
        String key = "groups/" + issue.getProject().getGroup().getId() + "/projects/"
                + issue.getProject().getId() + "/issues/" + issueId + "/" + UUID.randomUUID() + "." + image.extension();
        storage.put(key, image.bytes(), image.contentType());
        deleteOnRollback(key);
        ProjectIssueImage saved = images.saveAndFlush(new ProjectIssueImage(issue, actor, key,
                safeFilename(file.getOriginalFilename()), image.contentType(), image.bytes().length,
                image.checksum(), sortOrder == null ? images.countByIssueId(issueId) : sortOrder));
        return imageResponse(saved, actor);
    }

    @Transactional(readOnly = true)
    public ImageDownload downloadImage(Long userId, Long imageId) {
        ProjectIssueImage image = image(imageId);
        authorization.requireActiveMember(image.getIssue().getProject().getGroup().getId(), userId);
        FileStorage.StoredFile stored = storage.get(image.getStorageKey());
        return new ImageDownload(stored.content(), image.getContentType(), image.getOriginalFilename());
    }

    @Transactional
    public void deleteImage(Long userId, Long imageId) {
        ProjectIssueImage image = image(imageId);
        ProjectIssue issue = image.getIssue();
        GroupMember actor = actor(userId, issue);
        boolean uploader = image.getUploadedBy().getId().equals(actor.getId());
        if (!uploader && !canManage(issue, actor)) throw new ApplicationException(
                "PROJECT_ISSUE_IMAGE_DELETE_FORBIDDEN", HttpStatus.FORBIDDEN, "이미지를 삭제할 권한이 없습니다.");
        String key = image.getStorageKey(); images.delete(image); images.flush(); deleteAfterCommit(key);
    }

    private void archiveTree(ProjectIssue issue) {
        for (ProjectIssue child : issues.findAllByParentIdAndArchivedAtIsNullOrderBySortOrderAscIdAsc(issue.getId()))
            archiveTree(child);
        issue.archive();
    }
    private ProjectIssue validateParent(Project project, ProjectIssue.Level level, Long parentId) {
        if (level == ProjectIssue.Level.MAJOR) {
            if (parentId != null) throw hierarchy();
            return null;
        }
        if (parentId == null) throw hierarchy();
        ProjectIssue parent = issue(parentId);
        ProjectIssue.Level expected = level == ProjectIssue.Level.MIDDLE
                ? ProjectIssue.Level.MAJOR : ProjectIssue.Level.MIDDLE;
        if (!parent.getProject().getId().equals(project.getId()) || parent.getLevel() != expected) throw hierarchy();
        return parent;
    }
    private void requireManage(ProjectIssue issue, GroupMember actor) {
        if (!canManage(issue, actor)) throw new ApplicationException(
                "PROJECT_ISSUE_WRITE_FORBIDDEN", HttpStatus.FORBIDDEN, "이슈를 변경할 권한이 없습니다.");
    }
    private boolean canManage(ProjectIssue issue, GroupMember actor) {
        if (actor.getRole() == GroupMember.Role.LEADER) return true;
        Project project = issue.getProject();
        if (project.getLead() != null && project.getLead().getId().equals(actor.getId())) return true;
        return issue.getCreatedBy().getId().equals(actor.getId())
                || issue.getAssignee() != null && issue.getAssignee().getId().equals(actor.getId());
    }
    private void requireStructureManager(Project project, GroupMember actor) {
        boolean lead = project.getLead() != null && project.getLead().getId().equals(actor.getId());
        if (actor.getRole() != GroupMember.Role.LEADER && !lead) throw new ApplicationException(
                "PROJECT_STRUCTURE_WRITE_FORBIDDEN", HttpStatus.FORBIDDEN,
                "그룹 팀장 또는 프로젝트 리더만 분류를 변경할 수 있습니다.");
    }
    private void requireMutable(Project project) {
        if (project.getStatus() == Project.Status.ARCHIVED) throw new ApplicationException(
                "PROJECT_ARCHIVED", HttpStatus.CONFLICT, "보관된 프로젝트는 변경할 수 없습니다.");
    }
    private void requireVersion(ProjectIssue issue, long expected) {
        if (issue.getVersion() != expected) throw versionConflict("이슈");
    }
    private Project project(Long id) { return projects.findById(id).orElseThrow(() -> notFound("프로젝트")); }
    private ProjectIssue issue(Long id) { return issues.findByIdAndArchivedAtIsNull(id).orElseThrow(() -> notFound("이슈")); }
    private ProjectIssue active(ProjectIssue value) { if (value.getArchivedAt() != null) throw notFound("이슈"); return value; }
    private ProjectIssue leaf(Long id) {
        ProjectIssue value = issue(id);
        if (value.getLevel() != ProjectIssue.Level.ISSUE) throw new ApplicationException(
                "PROJECT_ISSUE_LEAF_REQUIRED", HttpStatus.BAD_REQUEST, "소분류 이슈에서만 사용할 수 있습니다.");
        return value;
    }
    private ProjectIssueImage image(Long id) { return images.findById(id).orElseThrow(() -> notFound("이미지")); }
    private GroupMember actor(Long userId, ProjectIssue issue) {
        return authorization.requireActiveMember(issue.getProject().getGroup().getId(), userId);
    }
    private GroupMember optionalMember(Project project, Long memberId) {
        return memberId == null ? null : authorization.requireActiveMemberById(project.getGroup().getId(), memberId);
    }
    private ProjectIssue.Level level(String value) { try { return ProjectIssue.Level.valueOf(value.trim().toUpperCase()); }
        catch (RuntimeException exception) { throw hierarchy(); } }
    private ProjectIssue.Status status(String value) { try { return ProjectIssue.Status.valueOf(value.trim().toUpperCase()); }
        catch (RuntimeException exception) { throw new ApplicationException(
                "PROJECT_ISSUE_STATUS_INVALID", HttpStatus.BAD_REQUEST, "올바른 이슈 상태를 선택해 주세요."); } }
    private ApplicationException hierarchy() { return new ApplicationException(
            "PROJECT_ISSUE_HIERARCHY_INVALID", HttpStatus.BAD_REQUEST,
            "대분류 아래 중분류, 중분류 아래 소분류 이슈만 만들 수 있습니다."); }
    private ApplicationException notFound(String name) { return new ApplicationException(
            "PROJECT_ISSUE_NOT_FOUND", HttpStatus.NOT_FOUND, name + "을(를) 찾을 수 없습니다."); }
    private ApplicationException versionConflict(String name) { return new ApplicationException(
            "PROJECT_ISSUE_VERSION_CONFLICT", HttpStatus.CONFLICT,
            name + "가 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요."); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private IssueNodeResponse response(ProjectIssue value, GroupMember viewer) {
        List<ProjectIssueChecklistItem> itemValues = value.getLevel() == ProjectIssue.Level.ISSUE
                ? checklist.findAllByIssueIdOrderBySortOrderAscIdAsc(value.getId()) : List.of();
        List<ProjectIssueImage> imageValues = value.getLevel() == ProjectIssue.Level.ISSUE
                ? images.findAllByIssueIdOrderBySortOrderAscIdAsc(value.getId()) : List.of();
        return response(value, viewer, itemValues, imageValues);
    }
    private IssueNodeResponse response(ProjectIssue value, GroupMember viewer,
            List<ProjectIssueChecklistItem> itemValues, List<ProjectIssueImage> imageValues) {
        return new IssueNodeResponse(value.getId(), value.getProject().getId(),
                value.getParent() == null ? null : value.getParent().getId(), value.getLevel().name(),
                value.getTitle(), value.getDescription(), value.getStatus().name(),
                value.getAssignee() == null ? null : value.getAssignee().getId(),
                value.getAssignee() == null ? null : value.getAssignee().getUser().getNickname(),
                value.getCreatedBy().getId(), value.getCreatedBy().getUser().getNickname(), value.getSortOrder(),
                value.getDueDate(), value.getVersion(), value.getCreatedAt(), value.getUpdatedAt(), value.getArchivedAt(),
                value.getArchivedAt() == null && value.getProject().getStatus() != Project.Status.ARCHIVED
                        && canManage(value, viewer), itemValues.stream().map(this::checklistResponse).toList(),
                imageValues.stream().map(image -> imageResponse(image, viewer)).toList());
    }
    private IssueChecklistResponse checklistResponse(ProjectIssueChecklistItem value) {
        return new IssueChecklistResponse(value.getId(), value.getContent(), value.isCompleted(),
                value.getCompletedBy() == null ? null : value.getCompletedBy().getId(), value.getCompletedAt(),
                value.getSortOrder(), value.getVersion(), value.getCreatedAt(), value.getUpdatedAt());
    }
    private IssueImageResponse imageResponse(ProjectIssueImage value, GroupMember viewer) {
        return new IssueImageResponse(value.getId(), value.getOriginalFilename(), value.getContentType(),
                value.getSizeBytes(), value.getUploadedBy().getId(), value.getUploadedBy().getUser().getNickname(),
                value.getSortOrder(), value.getCreatedAt(),
                "/project-issue-images/" + value.getId() + "/content",
                value.getUploadedBy().getId().equals(viewer.getId()) || canManage(value.getIssue(), viewer));
    }

    private ImageValue validateImage(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty() || file.getSize() > maxBytes) throw new ApplicationException(
                "PROJECT_ISSUE_IMAGE_INVALID", HttpStatus.BAD_REQUEST, "10MB 이하의 이미지를 선택해 주세요.");
        try {
            byte[] bytes = file.getBytes();
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if (input == null) throw invalidImage();
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalidImage();
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!IMAGE_FORMATS.contains(format) || reader.getWidth(0) < 1 || reader.getHeight(0) < 1
                            || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) throw invalidImage();
                    String extension = format.equals("jpeg") || format.equals("jpg") ? "jpg" : format;
                    return new ImageValue(bytes, extension, "image/" + (extension.equals("jpg") ? "jpeg" : extension), sha256(bytes));
                } finally { reader.dispose(); }
            }
        } catch (IOException exception) { throw invalidImage(); }
    }
    private ApplicationException invalidImage() { return new ApplicationException(
            "PROJECT_ISSUE_IMAGE_INVALID", HttpStatus.BAD_REQUEST,
            "4096px, 10MB 이하의 JPG, PNG 또는 GIF 이미지만 사용할 수 있습니다."); }
    private String safeFilename(String raw) {
        String value = raw == null ? "image" : raw.replace("\\", "/");
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\\"]", "_").trim();
        return value.isBlank() ? "image" : value.substring(0, Math.min(255, value.length()));
    }
    private String sha256(byte[] bytes) { try {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private void deleteOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) storage.delete(key);
            }
        });
    }
    private void deleteAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { storage.delete(key); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { storage.delete(key); }
        });
    }
    private record ImageValue(byte[] bytes, String extension, String contentType, String checksum) {}
    public record ImageDownload(byte[] content, String contentType, String filename) {}
}
