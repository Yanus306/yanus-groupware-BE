package com.yanus.attendance.attendance.presentation.dto;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceResponse(
        Long id,
        Long memberId,
        String memberName,
        LocalDate workDate,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        AttendanceStatus status
) {
    public static AttendanceResponse from(Attendance attendance) {
        return new AttendanceResponse(
                attendance.getId(),
                attendance.getMember().getId(),
                attendance.getMember().getName(),
                attendance.getWorkDate(),
                attendance.getCheckInTime(),
                attendance.getCheckOutTime(),
                attendance.getStatus()
        );
    }
}
