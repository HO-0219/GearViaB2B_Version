package com.teamproject.notification.application.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationResponse(Long id, String type, String title, String message,
            Long actorUserId, String actorNickname, Long groupId, String groupName, Long taskId, Long commentId,
            String targetUrl, boolean read, LocalDateTime readAt, LocalDateTime createdAt) {}

    public record NotificationPageResponse(List<NotificationResponse> items, Long nextCursor,
            boolean hasNext, long unreadCount) {}

    public record ReadAllResponse(int updatedCount) {}
}
