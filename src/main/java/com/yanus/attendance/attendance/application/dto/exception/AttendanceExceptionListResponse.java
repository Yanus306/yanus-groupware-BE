package com.yanus.attendance.attendance.application.dto.exception;

import java.time.LocalDate;
import java.util.List;

public record AttendanceExceptionListResponse(
        LocalDate date, AttendanceExceptionSummary summary, List<AttendanceExceptionResponse> items
) {
}
