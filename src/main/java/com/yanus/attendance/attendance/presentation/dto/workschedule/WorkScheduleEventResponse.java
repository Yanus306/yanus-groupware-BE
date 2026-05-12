package com.yanus.attendance.attendance.presentation.dto.workschedule;

import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEvent;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEventType;
import java.time.LocalDate;
import java.time.LocalTime;

public record WorkScheduleEventResponse(
        Long id,
        LocalDate date,
        WorkScheduleEventType eventType,
        LocalTime startTime,
        LocalTime endTime,
        boolean endsNextDay,
        Long memberId,
        String memberName,
        String teamName
) {
    public static WorkScheduleEventResponse from(WorkScheduleEvent event) {
        return new WorkScheduleEventResponse(
                event.getId(),
                event.getDate(),
                event.getEventType(),
                event.getStartTime(),
                event.getEndTime(),
                event.isEndsNextDay(),
                event.getMember().getId(),
                event.getMember().getName(),
                event.getMember().getTeam().getName()
        );
    }
}
