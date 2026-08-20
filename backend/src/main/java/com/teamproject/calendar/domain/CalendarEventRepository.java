package com.teamproject.calendar.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {
    List<CalendarEvent> findAllByGroupIdAndStartAtUtcLessThanAndEndAtUtcGreaterThanOrderByStartAtUtcAscIdAsc(
            Long groupId, LocalDateTime toUtc, LocalDateTime fromUtc);
}
