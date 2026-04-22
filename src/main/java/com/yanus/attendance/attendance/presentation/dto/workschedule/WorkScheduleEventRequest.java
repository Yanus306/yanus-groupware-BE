package com.yanus.attendance.attendance.presentation.dto.workschedule;

import java.time.LocalDate;
import java.time.LocalTime;

public record WorkScheduleEventRequest(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean endsNextDay
) {
}
