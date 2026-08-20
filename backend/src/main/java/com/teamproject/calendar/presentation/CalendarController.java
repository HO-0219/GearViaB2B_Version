package com.teamproject.calendar.presentation;

import com.teamproject.calendar.application.CalendarService;
import com.teamproject.calendar.application.dto.CalendarDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class CalendarController {
    private final CalendarService calendars;
    public CalendarController(CalendarService calendars) { this.calendars = calendars; }

    @GetMapping("/calendars/events")
    CalendarResponse list(Authentication authentication, @RequestParam(required = false) Long groupId,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return calendars.list((Long) authentication.getPrincipal(), groupId, from, to);
    }

    @PostMapping("/groups/{groupId}/calendar-events")
    @ResponseStatus(HttpStatus.CREATED)
    CalendarItemResponse create(Authentication authentication, @PathVariable Long groupId,
            @Valid @RequestBody CreateCalendarEventRequest request) {
        return calendars.create((Long) authentication.getPrincipal(), groupId, request);
    }

    @PatchMapping("/calendar-events/{eventId}")
    CalendarItemResponse update(Authentication authentication, @PathVariable Long eventId,
            @Valid @RequestBody UpdateCalendarEventRequest request) {
        return calendars.update((Long) authentication.getPrincipal(), eventId, request);
    }

    @DeleteMapping("/calendar-events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication authentication, @PathVariable Long eventId,
            @RequestParam Long expectedVersion) {
        calendars.delete((Long) authentication.getPrincipal(), eventId, expectedVersion);
    }
}
