package com.yanus.attendance.leave.application.dto;

import java.time.LocalDate;

public record LeaveCreateCommand(
        String category,
        String detail,
        LocalDate date
) {
}
