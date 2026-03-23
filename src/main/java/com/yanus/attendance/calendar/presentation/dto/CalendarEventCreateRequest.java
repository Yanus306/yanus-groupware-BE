package com.yanus.attendance.calendar.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record CalendarEventCreateRequest(
        String title,
        LocalDate startDate,
        LocalTime startTime,
        LocalDate endDate,
        LocalTime endTime
) {
}
