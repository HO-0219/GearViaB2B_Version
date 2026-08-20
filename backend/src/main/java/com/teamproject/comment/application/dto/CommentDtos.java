package com.teamproject.comment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class CommentDtos {
    private CommentDtos() {}

    public record CreateCommentRequest(
            @NotBlank @Size(max = 2000) String content,
            @Size(max = 20) List<@Positive Long> mentionedMemberIds) {}

    public record UpdateCommentRequest(
            @NotBlank @Size(max = 2000) String content,
            @Size(max = 20) List<@Positive Long> mentionedMemberIds,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record CommentMentionResponse(
            Long id, Long memberId, Long userId, String nickname) {}

    public record CommentResponse(
            Long id, Long taskId, Long authorMemberId, String authorNickname,
            String content, boolean deleted, boolean recordLocked,
            List<CommentMentionResponse> mentions, long version,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {}
}
