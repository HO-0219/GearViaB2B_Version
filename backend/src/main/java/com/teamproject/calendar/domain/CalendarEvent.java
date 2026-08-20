package com.teamproject.calendar.domain;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_events")
public class CalendarEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_member_id", nullable = false)
    private GroupMember createdBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Type type;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(length = 2000)
    private String description;
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAtUtc;
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAtUtc;
    @Column(nullable = false)
    private boolean allDay;
    @Column(length = 300)
    private String location;
    @Version
    private long version;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected CalendarEvent() {}

    public CalendarEvent(Group group, GroupMember createdBy, Type type, String title,
            String description, LocalDateTime startAtUtc, LocalDateTime endAtUtc,
            boolean allDay, String location) {
        this.group = group;
        this.createdBy = createdBy;
        this.type = type;
        this.title = title;
        this.description = description;
        this.startAtUtc = startAtUtc;
        this.endAtUtc = endAtUtc;
        this.allDay = allDay;
        this.location = location;
        this.createdAt = LocalDateTime.now(java.time.Clock.systemUTC());
        this.updatedAt = createdAt;
    }

    public void update(Type type, String title, String description, LocalDateTime startAtUtc,
            LocalDateTime endAtUtc, boolean allDay, String location) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.startAtUtc = startAtUtc;
        this.endAtUtc = endAtUtc;
        this.allDay = allDay;
        this.location = location;
        this.updatedAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public GroupMember getCreatedBy() { return createdBy; }
    public Type getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDateTime getStartAtUtc() { return startAtUtc; }
    public LocalDateTime getEndAtUtc() { return endAtUtc; }
    public boolean isAllDay() { return allDay; }
    public String getLocation() { return location; }
    public long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public enum Type { SCHEDULE, MEETING, VACATION, TODO }
}
