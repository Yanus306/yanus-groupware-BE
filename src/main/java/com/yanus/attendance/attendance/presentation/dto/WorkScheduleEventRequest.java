package com.yanus.attendance.attendance.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record WorkScheduleEventRequest(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {
}
