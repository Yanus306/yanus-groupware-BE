package com.yanus.attendance.attendance.presentation.dto.exception;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.exception.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AttendanceExceptionResponse(
        Long id,
        Long memberId,
        String memberName,
        String teamName,
        LocalDate workDate,
        AttendanceExceptionType type,
        AttendanceExceptionStatus status,
        String note,
        String reason,
        String approvedBy,
        LocalDateTime approvedAt,
        String resolvedBy,
        LocalDateTime resolvedAt,
        Long attendanceRecordId,
        LocalTime scheduledStartTime,
        LocalTime scheduledEndTime,
        boolean endsNextDay,
        LocalDateTime scheduledStartAt,
        LocalDateTime scheduledEndAt,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime
) {
    public static AttendanceExceptionResponse from(
            AttendanceException e,
            LocalTime scheduledStart,
            LocalTime scheduledEnd,
            boolean endsNextDay) {
        Attendance attendance = e.getAttendance();
        return new AttendanceExceptionResponse(
                e.getId(),
                e.getMember().getId(),
                e.getMember().getName(),
                e.getMember().getTeam().getName(),
                e.getWorkDate(),
                e.getType(),
                e.getStatus(),
                e.getNote(),
                e.getReason(),
                e.getApprovedBy(),
                e.getApprovedAt(),
                e.getResolvedBy(),
                e.getResolvedAt(),
                attendanceId(attendance),
                scheduledStart,
                scheduledEnd,
                endsNextDay,
                toDateTime(e.getWorkDate(), scheduledStart, false),
                toDateTime(e.getWorkDate(), scheduledEnd, endsNextDay),
                checkIn(attendance),
                checkOut(attendance)
        );
    }

    private static LocalDateTime toDateTime(LocalDate workDate, LocalTime time, boolean nextDay) {
        if (time == null) {
            return null;
        }
        if (nextDay) {
            return workDate.plusDays(1).atTime(time);
        }
        return workDate.atTime(time);
    }

    private static Long attendanceId(Attendance attendance) {
        if (attendance == null) {
            return null;
        }
        return attendance.getId();
    }

    private static LocalDateTime checkIn(Attendance attendance) {
        if (attendance == null) {
            return null;
        }
        return attendance.getCheckInTime();
    }

    private static LocalDateTime checkOut(Attendance attendance) {
        if (attendance == null) {
            return null;
        }
        return attendance.getCheckOutTime();
    }
}