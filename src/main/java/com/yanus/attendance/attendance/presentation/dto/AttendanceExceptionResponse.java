package com.yanus.attendance.attendance.presentation.dto;

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
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime
) {
    public static AttendanceExceptionResponse from(
            AttendanceException e,
            LocalTime scheduledStart, LocalTime scheduledEnd) {
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
                e.getAttendance() != null ? e.getAttendance().getId() : null,
                scheduledStart,
                scheduledEnd,
                e.getAttendance() != null ? e.getAttendance().getCheckInTime() : null,
                e.getAttendance() != null ? e.getAttendance().getCheckOutTime() : null
        );
    }
}