package com.yanus.attendance.attendance.application.dto.exception;

public record AttendanceExceptionSummary(
        int totalCount,
        int filteredCount,
        int openCount,
        int missedCheckInCount,
        int missedCheckOutCount,
        int lateCount,
        int noScheduleCount
) {
}
