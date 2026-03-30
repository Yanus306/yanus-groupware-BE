package com.yanus.attendance.attendance.presentation.dto;

import java.util.List;

public record AttendanceSettlementResponse(
        String yearMonth,
        Long memberId,
        String memberName,
        String teamName,
        int scheduledDays,
        int attendedDays,
        int lateDays,
        int totalLateMinutes,
        int lateFee,
        List<AttendanceSettlementItemResponse> items
) {
}
