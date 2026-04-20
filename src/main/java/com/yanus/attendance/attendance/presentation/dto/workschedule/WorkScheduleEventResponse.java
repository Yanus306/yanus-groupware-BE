package com.yanus.attendance.attendance.presentation.dto.workschedule;

import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEvent;
import java.time.LocalDate;
import java.time.LocalTime;

public record WorkScheduleEventResponse(
        Long id,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Long memberId,
        String memberName,
        String teamName
) {
    public static WorkScheduleEventResponse from(WorkScheduleEvent event) {
        return new WorkScheduleEventResponse(
                event.getId(),
                event.getDate(),
                event.getStartTime(),
                event.getEndTime(),
                event.getMember().getId(),
                event.getMember().getName(),
                event.getMember().getTeam().getName()
        );
    }
}
