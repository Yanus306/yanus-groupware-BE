package com.yanus.attendance.attendance.presentation.dto.attendance;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceStatus;
import com.yanus.attendance.member.domain.Member;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceRangeResponse(
        Long attendanceId,
        Long memberId,
        String memberName,
        String teamName,
        LocalDate workDate,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        AttendanceStatus status
) {
    public static AttendanceRangeResponse from(Attendance attendance) {
        Member member = attendance.getMember();
        return new AttendanceRangeResponse(
                attendance.getId(),
                member.getId(),
                member.getName(),
                member.getTeam() != null ? member.getTeam().getName() : null,
                attendance.getWorkDate(),
                attendance.getCheckInTime(),
                attendance.getCheckOutTime(),
                attendance.getStatus()
        );
    }
}