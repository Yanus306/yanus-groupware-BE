package com.yanus.attendance.leave.presentation.dto;

import com.yanus.attendance.leave.domain.LeaveCategory;
import com.yanus.attendance.leave.domain.LeaveRequest;
import com.yanus.attendance.leave.domain.LeaveStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveResponse(
        Long id,
        Long memberId,
        String memberName,
        LeaveCategory category,
        String detail,
        LocalDate date,
        LeaveStatus status,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {
    public static LeaveResponse from(LeaveRequest request) {
        return new LeaveResponse(
                request.getId(),
                request.getMember().getId(),
                request.getMember().getName(),
                request.getCategory(),
                request.getDetail(),
                request.getDate(),
                request.getStatus(),
                request.getSubmittedAt(),
                request.getReviewedAt()
        );
    }
}
