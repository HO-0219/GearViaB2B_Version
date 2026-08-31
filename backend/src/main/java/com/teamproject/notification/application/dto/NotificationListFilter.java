package com.teamproject.notification.application.dto;

import com.teamproject.notification.domain.Notification;

public record NotificationListFilter(Read read, Long groupId, Notification.Type type, Period period) {
    public enum Read { ALL, READ, UNREAD }
    public enum Period { ALL, TODAY, LAST_7_DAYS, LAST_30_DAYS }
    public NotificationListFilter { read = read == null ? Read.ALL : read; period = period == null ? Period.ALL : period; }
    public Boolean readValue() { return read == Read.ALL ? null : read == Read.READ; }
}
