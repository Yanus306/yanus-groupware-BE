package com.yanus.attendance.attendance.presentation.dto.exception;

public record AttendanceExceptionUpdateRequest(
        String note, String reason
) {
}
