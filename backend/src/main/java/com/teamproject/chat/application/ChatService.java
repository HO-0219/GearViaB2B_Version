package com.teamproject.chat.application;

import com.teamproject.chat.application.dto.ChatDtos.*;
import com.teamproject.chat.domain.*;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.common.storage.GroupStorageQuotaService;
import com.teamproject.group.application.*;
import com.teamproject.group.domain.*;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.project.domain.*;
import com.teamproject.resource.storage.FileStorage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Service
public class ChatService {
    private static final Set<String> EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "gif", "txt", "csv", "docx", "xlsx", "pptx", "zip");
    private final ChatChannelRepository channels;
    private final ChatMessageRepository messages;
    private final GroupAuthorization authorization;
    private final GroupRepository groups;
    private final GroupFeaturePolicy features;
    private final ProjectRepository projects;
    private final ProjectIssueRepository issues;
    private final GroupStorageQuotaService quota;
    private final FileStorage storage;
    private final ChatRateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final NotificationService notifications;

    public ChatService(ChatChannelRepository channels, ChatMessageRepository messages,
            GroupAuthorization authorization, GroupRepository groups, GroupFeaturePolicy features, ProjectRepository projects,
            ProjectIssueRepository issues, GroupStorageQuotaService quota, FileStorage storage,
            ChatRateLimiter rateLimiter, ApplicationEventPublisher events, NotificationService notifications) {
        this.channels = channels; this.messages = messages; this.authorization = authorization;
        this.groups = groups; this.features = features; this.projects = projects; this.issues = issues; this.quota = quota;
        this.storage = storage; this.rateLimiter = rateLimiter; this.events = events; this.notifications = notifications;
    }

    @Transactional
    public List<ChannelResponse> channels(Long userId, Long groupId) {
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        requireTeam(member.getGroup()); ensureGeneral(member);
        var policy = features.policy(userId, groupId);
        return channels.findAllByGroupIdAndArchivedAtIsNullOrderByCreatedAtAscIdAsc(groupId).stream()
                .filter(value -> policy.multipleChatChannels() || value.getChannelType() == ChatChannel.Type.GENERAL)
                .map(value -> channelResponse(value, member, policy.messageRetentionDays())).toList();
    }

    @Transactional
    public ChannelResponse createChannel(Long userId, Long groupId, CreateChannelRequest request) {
        GroupMember actor = authorization.requireActiveMember(groupId, userId); requireTeam(actor.getGroup());
        var policy = features.policy(userId, groupId);
        if (!policy.multipleChatChannels()) throw new ApplicationException(
                "CHAT_PAID_CHANNEL_REQUIRED", HttpStatus.FORBIDDEN, "유료 그룹에서만 여러 채팅방을 만들 수 있습니다.");
        ensureGeneral(actor);
        if (channels.countByGroupIdAndArchivedAtIsNull(groupId) >= policy.chatChannelLimit()) throw new ApplicationException(
                "CHAT_CHANNEL_LIMIT", HttpStatus.CONFLICT, "채팅방 생성 한도에 도달했습니다.");
        Project project = optionalProject(groupId, request.projectId());
        ProjectIssue issue = optionalMajorIssue(project, request.issueNodeId());
        requireChannelCreator(actor, project);
        ChatChannel saved = channels.saveAndFlush(new ChatChannel(actor.getGroup(), project, issue, actor,
                UUID.randomUUID().toString(), request.name().trim(), ChatChannel.Type.TOPIC));
        return channelResponse(saved, actor, policy.messageRetentionDays());
    }

    @Transactional(readOnly = true)
    public MessagePageResponse history(Long userId, Long channelId, Long beforeId, int requestedLimit) {
        ChatAccess access = access(userId, channelId);
        int limit = Math.max(1, Math.min(100, requestedLimit));
        LocalDateTime cutoff = LocalDateTime.now().minusDays(access.retentionDays());
        List<ChatMessage> values = new ArrayList<>(messages.history(channelId, cutoff, beforeId, PageRequest.of(0, limit)));
        Long next = values.size() == limit ? values.getLast().getId() : null;
        Collections.reverse(values);
        return new MessagePageResponse(values.stream().map(this::messageResponse).toList(), next, access.retentionDays());
    }

    @Transactional
    public MessageResponse sendText(Long userId, Long channelId, String raw) {
        ChatAccess access = access(userId, channelId); rateLimiter.check(userId);
        String content = raw == null ? "" : raw.trim();
        if (content.isBlank() || content.length() > 4000) throw new ApplicationException(
                "CHAT_MESSAGE_INVALID", HttpStatus.BAD_REQUEST, "1~4000자의 메시지를 입력해 주세요.");
        ChatMessage saved = messages.saveAndFlush(ChatMessage.text(access.channel(), access.member(), content));
        notifications.chatMessage(access.member(), channelId, saved.getId(), access.channel().getName());
        MessageResponse response = messageResponse(saved); events.publishEvent(new ChatMessageEvent(channelId, response));
        return response;
    }

    @Transactional
    public MessageResponse upload(Long userId, Long channelId, String caption, MultipartFile file) {
        ChatAccess access = access(userId, channelId); rateLimiter.check(userId);
        var policy = features.policy(userId, access.channel().getGroup().getId());
        FileValue value = validate(file, policy.attachmentLimitBytes());
        quota.requireCapacity(userId, access.channel().getGroup().getId(), value.bytes().length);
        String filename = safeFilename(file.getOriginalFilename());
        String key = "groups/" + access.channel().getGroup().getId() + "/chat/" + channelId + "/"
                + UUID.randomUUID() + "." + value.extension();
        storage.put(key, value.bytes(), value.contentType()); deleteOnRollback(key);
        String cleanCaption = caption == null || caption.isBlank() ? null : caption.trim();
        if (cleanCaption != null && cleanCaption.length() > 1000) throw new ApplicationException(
                "CHAT_MESSAGE_INVALID", HttpStatus.BAD_REQUEST, "첨부 설명은 1000자 이하로 입력해 주세요.");
        ChatMessage.Type type = value.image() ? ChatMessage.Type.IMAGE : ChatMessage.Type.FILE;
        ChatMessage saved = messages.saveAndFlush(ChatMessage.attachment(access.channel(), access.member(), type,
                cleanCaption, key, filename, value.contentType(), value.bytes().length, sha256(value.bytes())));
        notifications.chatMessage(access.member(), channelId, saved.getId(), access.channel().getName());
        MessageResponse response = messageResponse(saved); events.publishEvent(new ChatMessageEvent(channelId, response));
        return response;
    }

    @Transactional(readOnly = true)
    public Download download(Long userId, Long messageId) {
        ChatMessage value = messages.findById(messageId).orElseThrow(() -> notFound("메시지"));
        ChatAccess access = access(userId, value.getChannel().getId());
        if (value.getStorageKey() == null || value.getCreatedAt().isBefore(LocalDateTime.now().minusDays(access.retentionDays())))
            throw notFound("파일");
        var stored = storage.get(value.getStorageKey());
        return new Download(stored.content(), value.getContentType(), value.getOriginalFilename());
    }

    @Transactional(readOnly = true)
    public void requireChannelAccess(Long userId, Long channelId) { access(userId, channelId); }

    private ChatAccess access(Long userId, Long channelId) {
        ChatChannel channel = channels.findByIdAndArchivedAtIsNull(channelId).orElseThrow(() -> notFound("채팅방"));
        GroupMember member = authorization.requireActiveMember(channel.getGroup().getId(), userId);
        var policy = features.policy(userId, channel.getGroup().getId());
        if (!policy.multipleChatChannels() && channel.getChannelType() != ChatChannel.Type.GENERAL)
            throw new ApplicationException("CHAT_CHANNEL_PLAN_RESTRICTED", HttpStatus.FORBIDDEN,
                    "현재 플랜에서는 그룹 공용 채팅방만 사용할 수 있습니다.");
        return new ChatAccess(channel, member, policy.messageRetentionDays());
    }
    private void ensureGeneral(GroupMember actor) {
        Long groupId = actor.getGroup().getId();
        groups.findByIdForUpdate(groupId).orElseThrow(() -> notFound("그룹"));
        if (channels.findByGroupIdAndChannelKeyAndArchivedAtIsNull(groupId, "general").isPresent()) return;
        channels.saveAndFlush(new ChatChannel(actor.getGroup(), null, null, actor,
                "general", "그룹 공용", ChatChannel.Type.GENERAL));
    }
    private Project optionalProject(Long groupId, Long projectId) {
        if (projectId == null) return null;
        Project value = projects.findById(projectId).orElseThrow(() -> notFound("프로젝트"));
        if (!value.getGroup().getId().equals(groupId) || value.getStatus() == Project.Status.ARCHIVED) throw notFound("프로젝트");
        return value;
    }
    private ProjectIssue optionalMajorIssue(Project project, Long issueId) {
        if (issueId == null) return null;
        if (project == null) throw new ApplicationException("CHAT_CHANNEL_SCOPE_INVALID", HttpStatus.BAD_REQUEST,
                "대주제 채팅방에는 프로젝트를 선택해 주세요.");
        ProjectIssue value = issues.findByIdAndArchivedAtIsNull(issueId).orElseThrow(() -> notFound("대주제"));
        if (!value.getProject().getId().equals(project.getId()) || value.getLevel() != ProjectIssue.Level.MAJOR)
            throw notFound("대주제");
        return value;
    }
    private void requireChannelCreator(GroupMember actor, Project project) {
        boolean projectLead = project != null && project.getLead() != null && project.getLead().getId().equals(actor.getId());
        if (actor.getRole() != GroupMember.Role.LEADER && !projectLead) throw new ApplicationException(
                "CHAT_CHANNEL_CREATE_FORBIDDEN", HttpStatus.FORBIDDEN,
                "그룹 팀장 또는 프로젝트 리더만 채팅방을 만들 수 있습니다.");
    }
    private ChannelResponse channelResponse(ChatChannel value, GroupMember viewer, int retention) {
        boolean projectLead = value.getProject() != null && value.getProject().getLead() != null
                && value.getProject().getLead().getId().equals(viewer.getId());
        return new ChannelResponse(value.getId(), value.getGroup().getId(), value.getName(),
                value.getChannelType().name(), value.getProject() == null ? null : value.getProject().getId(),
                value.getProject() == null ? null : value.getProject().getName(),
                value.getIssueNode() == null ? null : value.getIssueNode().getId(),
                value.getIssueNode() == null ? null : value.getIssueNode().getTitle(), value.getCreatedAt(), retention,
                viewer.getRole() == GroupMember.Role.LEADER || projectLead);
    }
    private MessageResponse messageResponse(ChatMessage value) {
        return new MessageResponse(value.getId(), value.getChannel().getId(), value.getMessageType().name(),
                value.getContent(), value.getSender().getId(), value.getSender().getUser().getNickname(),
                value.getSender().getUser().getProfileImageUrl(), value.getOriginalFilename(), value.getContentType(),
                value.getSizeBytes(), value.getStorageKey() == null ? null : "/chat/messages/" + value.getId() + "/content",
                value.getCreatedAt());
    }
    private void requireTeam(Group group) { if (group.getType() != Group.Type.TEAM) throw new ApplicationException(
            "TEAM_CHAT_REQUIRED", HttpStatus.BAD_REQUEST, "팀 그룹에서만 채팅을 사용할 수 있습니다."); }
    private ApplicationException notFound(String name) { return new ApplicationException(
            "CHAT_NOT_FOUND", HttpStatus.NOT_FOUND, name + "을(를) 찾을 수 없습니다."); }
    private FileValue validate(MultipartFile file, long limit) {
        if (file == null || file.isEmpty() || file.getSize() > limit) throw invalid("현재 플랜의 파일당 한도를 확인해 주세요.");
        String filename = safeFilename(file.getOriginalFilename()); String extension = extension(filename);
        if (!EXTENSIONS.contains(extension)) throw invalid("허용되지 않는 파일 형식입니다.");
        try { byte[] bytes = file.getBytes(); if (!matches(extension, bytes)) throw invalid("파일 확장자와 실제 형식이 일치하지 않습니다.");
            boolean image = Set.of("png", "jpg", "jpeg", "gif").contains(extension);
            if (image && !validImage(bytes)) throw invalid("4096px 이하의 올바른 이미지를 선택해 주세요.");
            return new FileValue(bytes, extension, contentType(extension), image);
        } catch (java.io.IOException exception) { throw invalid("파일을 읽을 수 없습니다."); }
    }
    private boolean validImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try { reader.setInput(input, true, true);
                int width = reader.getWidth(0); int height = reader.getHeight(0);
                return width > 0 && height > 0 && width <= 4096 && height <= 4096;
            } finally { reader.dispose(); }
        } catch (Exception exception) { return false; }
    }
    private boolean matches(String extension, byte[] bytes) {
        if (Set.of("txt", "csv").contains(extension)) { for (byte value : bytes) if (value == 0) return false; return true; }
        if ("pdf".equals(extension)) return starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
        if (Set.of("docx", "xlsx", "pptx", "zip").contains(extension)) return starts(bytes, new byte[]{0x50, 0x4b});
        if ("png".equals(extension)) return starts(bytes, new byte[]{(byte)0x89,0x50,0x4e,0x47});
        if (Set.of("jpg", "jpeg").contains(extension)) return starts(bytes, new byte[]{(byte)0xff,(byte)0xd8});
        return "gif".equals(extension) && starts(bytes, "GIF8".getBytes(StandardCharsets.US_ASCII));
    }
    private boolean starts(byte[] value, byte[] prefix) { if (value.length < prefix.length) return false;
        for (int i=0;i<prefix.length;i++) if (value[i]!=prefix[i]) return false; return true; }
    private String safeFilename(String raw) { String value = raw == null ? "file" : raw.replace("\\", "/");
        value = value.substring(value.lastIndexOf('/')+1).replaceAll("[\\r\\n\\\"]", "_").trim();
        if (value.isBlank() || value.length()>255) throw invalid("올바른 파일 이름이 아닙니다."); return value; }
    private String extension(String name) { int dot=name.lastIndexOf('.'); return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT); }
    private String contentType(String e) { return switch(e) { case "pdf"->"application/pdf";case "png"->"image/png";
        case "jpg","jpeg"->"image/jpeg";case "gif"->"image/gif";case "txt"->"text/plain;charset=UTF-8";
        case "csv"->"text/csv;charset=UTF-8";case "docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        case "xlsx"->"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        case "pptx"->"application/vnd.openxmlformats-officedocument.presentationml.presentation";default->"application/zip";}; }
    private String sha256(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch(Exception exception){throw new IllegalStateException(exception);} }
    private ApplicationException invalid(String message) { return new ApplicationException("CHAT_FILE_INVALID", HttpStatus.BAD_REQUEST, message); }
    private void deleteOnRollback(String key) { if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storage.delete(key);}}); }
    private record ChatAccess(ChatChannel channel, GroupMember member, int retentionDays) {}
    private record FileValue(byte[] bytes, String extension, String contentType, boolean image) {}
    public record Download(byte[] content, String contentType, String filename) {}
}
