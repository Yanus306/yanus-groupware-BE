package com.yanus.attendance.leave.application.dto;

import com.yanus.attendance.leave.domain.LeaveCategory;
import java.time.LocalDate;

public record LeaveCreateCommand(
        LeaveCategory category,
        String detail,
        LocalDate date
) {
}
