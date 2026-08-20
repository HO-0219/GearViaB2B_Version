package com.teamproject.notification.application;

public record PushNotificationEvent(
        Long userId, Long notificationId, String notificationType,
        Long groupId, Long taskId, Long commentId,
        String title, String message, String targetUrl, String tag) {}
