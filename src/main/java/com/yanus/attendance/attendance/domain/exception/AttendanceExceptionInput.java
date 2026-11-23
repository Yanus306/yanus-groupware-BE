package com.yanus.attendance.attendance.domain.exception;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEvent;
import com.yanus.attendance.member.domain.Member;

public record AttendanceExceptionInput(
        Member member,
        WorkSchedule weeklySchedule,
        WorkScheduleEvent scheduleEvent,
        Attendance attendance,
        AttendanceExceptionType existingType
) {
}
