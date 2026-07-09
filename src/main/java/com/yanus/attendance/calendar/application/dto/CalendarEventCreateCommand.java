package com.yanus.attendance.calendar.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record CalendarEventCreateCommand(
        String title,
        LocalDate startDate,
        LocalTime startTime,
        LocalDate endDate,
        LocalTime endTime
) {
}
