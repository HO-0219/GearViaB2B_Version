package com.teamproject.comment.application;

import com.teamproject.comment.application.dto.CommentDtos.CommentResponse;
import com.teamproject.comment.application.dto.CommentDtos.CommentMentionResponse;
import com.teamproject.comment.application.dto.CommentDtos.CreateCommentRequest;
import com.teamproject.comment.application.dto.CommentDtos.UpdateCommentRequest;
import com.teamproject.comment.domain.TaskComment;
import com.teamproject.comment.domain.TaskCommentRepository;
import com.teamproject.comment.domain.CommentMention;
import com.teamproject.comment.domain.CommentMentionRepository;
import com.teamproject.comment.domain.CommentRevision;
import com.teamproject.comment.domain.CommentRevisionRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.task.domain.TaskStatusHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.regex.Pattern;

@Service
public class TaskCommentService {
    private static final String DELETED_CONTENT = "삭제된 댓글입니다.";
    private static final Duration EDIT_WINDOW = Duration.ofMinutes(15);
    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final CommentMentionRepository mentions;
    private final CommentRevisionRepository revisions;
    private final GroupAuthorization authorization;
    private final GroupMemberRepository groupMembers;
    private final NotificationService notifications;
    private final TaskStatusHistoryRepository histories;

    public TaskCommentService(TaskRepository tasks, TaskCommentRepository comments,
            CommentMentionRepository mentions, CommentRevisionRepository revisions,
            GroupAuthorization authorization, GroupMemberRepository groupMembers,
            NotificationService notifications,
            TaskStatusHistoryRepository histories) {
        this.tasks = tasks;
        this.comments = comments;
        this.mentions = mentions;
        this.revisions = revisions;
        this.authorization = authorization;
        this.groupMembers = groupMembers;
        this.notifications = notifications;
        this.histories = histories;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long userId, Long taskId) {
        Task task = task(taskId);
        authorization.requireActiveMember(task.getGroup().getId(), userId);
        return comments.findAllByTaskIdOrderByCreatedAtAscIdAsc(taskId).stream()
                .map(this::response).toList();
    }

    @Transactional
    public CommentResponse create(Long userId, Long taskId, CreateCommentRequest request) {
        Task task = task(taskId);
        GroupMember author = authorization.requireActiveMember(task.getGroup().getId(), userId);
        TaskComment comment = comments.save(new TaskComment(task, author, request.content().trim()));
        List<GroupMember> mentionedMembers = replaceMentions(
                comment, request.content(), request.mentionedMemberIds());
        if (!mentionedMembers.isEmpty()) notifications.commentMentioned(comment, mentionedMembers);
        if (task.getAssignee() != null && mentionedMembers.stream()
                .noneMatch(member -> member.getId().equals(task.getAssignee().getId()))) {
            notifications.commentCreated(comment, List.of(task.getAssignee()));
        }
        return response(comment);
    }

    @Transactional
    public CommentResponse update(Long userId, Long commentId, UpdateCommentRequest request) {
        TaskComment comment = comment(commentId);
        GroupMember actor = authorization.requireActiveMember(comment.getTask().getGroup().getId(), userId);
        requireAuthor(comment, actor);
        requireActive(comment);
        requireMutableRecord(comment);
        requireVersion(comment, request.expectedVersion());
        Set<Long> previousMentionIds = mentions.findAllByCommentIdOrderByIdAsc(comment.getId()).stream()
                .map(mention -> mention.getMentionedMember().getId()).collect(java.util.stream.Collectors.toSet());
        revisions.save(new CommentRevision(comment, actor, comment.getContent(), request.content().trim()));
        comment.update(request.content().trim());
        comments.flush();
        List<GroupMember> mentionedMembers = replaceMentions(
                comment, request.content(), request.mentionedMemberIds());
        notifications.commentMentioned(comment, mentionedMembers.stream()
                .filter(member -> !previousMentionIds.contains(member.getId())).toList());
        return response(comment);
    }

    @Transactional
    public void delete(Long userId, Long commentId, Long expectedVersion) {
        TaskComment comment = comment(commentId);
        GroupMember actor = authorization.requireActiveMember(comment.getTask().getGroup().getId(), userId);
        requireAuthor(comment, actor);
        requireActive(comment);
        requireMutableRecord(comment);
        requireVersion(comment, expectedVersion);
        comment.softDelete();
        comments.flush();
    }

    private void requireAuthor(TaskComment comment, GroupMember actor) {
        if (!comment.getAuthor().getId().equals(actor.getId())) {
            throw new ApplicationException("COMMENT_AUTHOR_REQUIRED", HttpStatus.FORBIDDEN,
                    "댓글 작성자만 수정하거나 삭제할 수 있습니다.");
        }
    }

    private void requireActive(TaskComment comment) {
        if (comment.isDeleted()) {
            throw new ApplicationException("COMMENT_ALREADY_DELETED", HttpStatus.CONFLICT,
                    "이미 삭제된 댓글입니다.");
        }
    }

    private void requireMutableRecord(TaskComment comment) {
        if (isRecordLocked(comment)) {
            throw new ApplicationException("COMMENT_RECORD_LOCKED", HttpStatus.CONFLICT,
                    "종료된 업무의 기존 댓글은 수정하거나 삭제할 수 없습니다.");
        }
        if (Duration.between(comment.getCreatedAt(), LocalDateTime.now()).compareTo(EDIT_WINDOW) > 0) {
            throw new ApplicationException("COMMENT_EDIT_WINDOW_EXPIRED", HttpStatus.CONFLICT,
                    "댓글은 작성 후 15분 동안만 수정하거나 삭제할 수 있습니다.");
        }
    }

    private boolean isRecordLocked(TaskComment comment) {
        Task.Status status = comment.getTask().getStatus();
        if (status == Task.Status.COMPLETED || status == Task.Status.REJECTED
                || status == Task.Status.CANCELLED) return true;
        return histories.existsByTaskIdAndFromStatusAndToStatusAndCreatedAtAfter(
                comment.getTask().getId(), Task.Status.COMPLETED, Task.Status.IN_PROGRESS,
                comment.getCreatedAt());
    }

    private void requireVersion(TaskComment comment, Long expectedVersion) {
        if (comment.getVersion() != expectedVersion) {
            throw new ApplicationException("COMMENT_VERSION_CONFLICT", HttpStatus.CONFLICT,
                    "댓글이 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
        }
    }

    private CommentResponse response(TaskComment comment) {
        return new CommentResponse(comment.getId(), comment.getTask().getId(),
                comment.getAuthor().getId(), comment.getAuthor().getUser().getNickname(),
                comment.isDeleted() ? DELETED_CONTENT : comment.getContent(), comment.isDeleted(),
                isRecordLocked(comment),
                comment.isDeleted() ? List.of() : mentions.findAllByCommentIdOrderByIdAsc(comment.getId()).stream()
                        .map(this::mentionResponse).toList(),
                comment.getVersion(), comment.getCreatedAt(), comment.getUpdatedAt(), comment.getDeletedAt());
    }

    private List<GroupMember> replaceMentions(
            TaskComment comment, String content, List<Long> mentionedMemberIds) {
        mentions.deleteAllByCommentId(comment.getId());
        mentions.flush();
        Long groupId = comment.getTask().getGroup().getId();
        var mentionedMembersById = new java.util.LinkedHashMap<Long, GroupMember>();
        if (mentionedMemberIds != null) {
            new LinkedHashSet<>(mentionedMemberIds).stream()
                    .map(memberId -> authorization.requireActiveMemberById(groupId, memberId))
                    .forEach(member -> mentionedMembersById.put(member.getId(), member));
        }
        groupMembers.findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(
                        groupId, GroupMember.Status.ACTIVE).stream()
                .filter(member -> containsNicknameMention(content, member.getUser().getNickname()))
                .forEach(member -> mentionedMembersById.putIfAbsent(member.getId(), member));
        if (mentionedMembersById.size() > 20) {
            throw new ApplicationException("COMMENT_MENTION_LIMIT_EXCEEDED", HttpStatus.BAD_REQUEST,
                    "한 댓글에서 최대 20명까지 멘션할 수 있습니다.");
        }
        List<GroupMember> mentionedMembers = List.copyOf(mentionedMembersById.values());
        List<CommentMention> replacements = mentionedMembers.stream()
                .map(member -> new CommentMention(comment, member)).toList();
        mentions.saveAll(replacements);
        mentions.flush();
        return mentionedMembers;
    }

    private boolean containsNicknameMention(String content, String nickname) {
        if (content == null || nickname == null || nickname.isBlank()) return false;
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}_])@"
                + Pattern.quote(nickname.trim()) + "(?![\\p{L}\\p{N}_])");
        return pattern.matcher(content).find();
    }

    private CommentMentionResponse mentionResponse(CommentMention mention) {
        GroupMember member = mention.getMentionedMember();
        return new CommentMentionResponse(mention.getId(), member.getId(),
                member.getUser().getId(), member.getUser().getNickname());
    }

    private Task task(Long taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new ApplicationException(
                "TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다."));
    }

    private TaskComment comment(Long commentId) {
        return comments.findById(commentId).orElseThrow(() -> new ApplicationException(
                "COMMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));
    }
}
