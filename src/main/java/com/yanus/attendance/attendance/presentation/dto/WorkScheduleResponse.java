package com.yanus.attendance.attendance.presentation.dto;

import com.yanus.attendance.attendance.domain.workschedule.WeekPattern;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkScheduleResponse(
        Long id,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        WeekPattern weekPattern
) {
    public static WorkScheduleResponse from(WorkSchedule schedule) {
        return new WorkScheduleResponse(
                schedule.getId(),
                schedule.getDayOfWeek(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getWeekPattern()
        );
    }
}
