package com.yanus.attendance.leave.presentation.dto;

import java.time.LocalDate;

public record LeaveCreateRequest(
        String category,
        String detail,
        LocalDate date
) {
}
