package com.yanus.attendance.attendance.presentation.dto.setting;

import com.yanus.attendance.attendance.domain.attendance.AttendanceSettlementStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AttendanceSettlementItemResponse(
        LocalDate date,
        LocalTime scheduledStartTime,
        LocalTime scheduledEndTime,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        int lateMinutes,
        int fee,
        AttendanceSettlementStatus status
) {
}
