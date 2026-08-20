package com.teamproject.chat.application.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() {}
    public record CreateChannelRequest(
            @NotBlank @Size(max = 80) String name, Long projectId, Long issueNodeId) {}
    public record SendTextRequest(@NotBlank @Size(max = 4000) String content) {}
    public record ChannelResponse(Long id, Long groupId, String name, String type,
            Long projectId, String projectName, Long issueNodeId, String issueNodeTitle,
            LocalDateTime createdAt, int retentionDays, boolean canCreateChannels) {}
    public record MessageResponse(Long id, Long channelId, String type, String content,
            Long senderMemberId, String senderNickname, String senderProfileImageUrl,
            String originalFilename, String contentType, Long sizeBytes, String contentUrl,
            LocalDateTime createdAt) {}
    public record MessagePageResponse(List<MessageResponse> items, Long nextBeforeId, int retentionDays) {}
    public record SocketTicketResponse(String ticket, long expiresInSeconds) {}
}
