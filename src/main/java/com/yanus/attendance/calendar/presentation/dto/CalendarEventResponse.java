package com.yanus.attendance.calendar.presentation.dto;

import com.yanus.attendance.calendar.domain.CalendarEvent;
import java.time.LocalDate;
import java.time.LocalTime;

public record CalendarEventResponse(
        Long id,
        String title,
        LocalDate startDate,
        LocalTime startTime,
        LocalDate endDate,
        LocalTime endTime,
        Long createdById,
        String createdByName
) {
    public static CalendarEventResponse from(CalendarEvent event) {
        return new CalendarEventResponse(
                event.getId(),
                event.getTitle(),
                event.getStartDate(),
                event.getStartTime(),
                event.getEndDate(),
                event.getEndTime(),
                event.getCreatedBy().getId(),
                event.getCreatedBy().getName()
        );
    }
}
