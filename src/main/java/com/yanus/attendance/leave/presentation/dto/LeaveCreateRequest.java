package com.yanus.attendance.leave.presentation.dto;

import com.yanus.attendance.leave.domain.LeaveCategory;
import java.time.LocalDate;

public record LeaveCreateRequest(
        LeaveCategory category,
        String detail,
        LocalDate date
) {
}
