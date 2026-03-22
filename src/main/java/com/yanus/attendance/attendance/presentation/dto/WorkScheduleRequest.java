package com.yanus.attendance.attendance.presentation.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkScheduleRequest(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
}
