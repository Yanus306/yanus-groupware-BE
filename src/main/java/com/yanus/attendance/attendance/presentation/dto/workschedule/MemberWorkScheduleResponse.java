package com.yanus.attendance.attendance.presentation.dto.workschedule;

import java.util.List;

public record MemberWorkScheduleResponse(
        Long memberId,
        String memberName,
        String teamName,
        List<WorkScheduleResponse> workSchedules
) {
}
