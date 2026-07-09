package com.yanus.attendance.calendar.presentation.dto;

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
    public static CalendarEventResponse from(com.yanus.attendance.calendar.application.dto.CalendarEventResponse response) {
        return new CalendarEventResponse(
                response.id(),
                response.title(),
                response.startDate(),
                response.startTime(),
                response.endDate(),
                response.endTime(),
                response.createdById(),
                response.createdByName()
        );
    }
}
