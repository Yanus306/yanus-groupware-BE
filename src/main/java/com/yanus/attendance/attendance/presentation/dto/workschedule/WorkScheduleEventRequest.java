package com.yanus.attendance.attendance.presentation.dto.workschedule;

import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEventType;
import java.time.LocalDate;
import java.time.LocalTime;

public record WorkScheduleEventRequest(
        LocalDate date,
        WorkScheduleEventType eventType,
        LocalTime startTime,
        LocalTime endTime,
        boolean endsNextDay,
        String reason
) {
    public WorkScheduleEventRequest(
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            boolean endsNextDay
    ) {
        this(date, WorkScheduleEventType.WORKING, startTime, endTime, endsNextDay, null);
    }

    public WorkScheduleEventType eventTypeOrDefault() {
        if (eventType == null) {
            return WorkScheduleEventType.WORKING;
        }
        return eventType;
    }
}
