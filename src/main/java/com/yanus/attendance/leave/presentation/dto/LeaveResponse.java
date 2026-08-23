package com.yanus.attendance.leave.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveResponse(
        Long id,
        Long memberId,
        String memberName,
        String category,
        String detail,
        LocalDate date,
        String status,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {
    public static LeaveResponse from(com.yanus.attendance.leave.application.dto.LeaveResponse response) {
        return new LeaveResponse(
                response.id(),
                response.memberId(),
                response.memberName(),
                response.category().name(),
                response.detail(),
                response.date(),
                response.status().name(),
                response.submittedAt(),
                response.reviewedAt()
        );
    }
}
