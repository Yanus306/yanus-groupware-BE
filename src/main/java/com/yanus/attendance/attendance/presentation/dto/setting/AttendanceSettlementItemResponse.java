package com.yanus.attendance.attendance.presentation.dto.setting;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceSettlementStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AttendanceSettlementItemResponse(
        LocalDate date,
        LocalTime scheduledStartTime,
        LocalTime scheduledEndTime,
        boolean endsNextDay,
        LocalDateTime scheduledStartAt,
        LocalDateTime scheduledEndAt,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        int lateMinutes,
        int fee,
        AttendanceSettlementStatus status
) {
    public static AttendanceSettlementItemResponse noSchedule(LocalDate date) {
        return new AttendanceSettlementItemResponse(
                date, null, null, false, null, null, null, null,
                0, 0, AttendanceSettlementStatus.NO_SCHEDULE
        );
    }

    public static AttendanceSettlementItemResponse absent(LocalDate date, ScheduledWindow window) {
        return new AttendanceSettlementItemResponse(
                date,
                window.start(), window.end(), window.endsNextDay(),
                date.atTime(window.start()),
                window.endsNextDay() ? date.plusDays(1).atTime(window.end()) : date.atTime(window.end()),
                null, null,
                0, 0, AttendanceSettlementStatus.ABSENT
        );
    }

    public static AttendanceSettlementItemResponse of(
            LocalDate date, ScheduledWindow window, Attendance attendance,
            int lateMinutes, int fee, AttendanceSettlementStatus status
    ) {
        return new AttendanceSettlementItemResponse(
                date,
                window.start(), window.end(), window.endsNextDay(),
                date.atTime(window.start()),
                window.endsNextDay() ? date.plusDays(1).atTime(window.end()) : date.atTime(window.end()),
                attendance.getCheckInTime(), attendance.getCheckOutTime(),
                lateMinutes, fee, status);
    }
}
