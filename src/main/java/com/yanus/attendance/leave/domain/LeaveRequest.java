package com.yanus.attendance.leave.domain;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leave_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    private LeaveCategory category;

    private String detail;

    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private LeaveStatus status;

    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Member reviewedBy;

    private LocalDateTime reviewedAt;

    public static LeaveRequest create(Member member, LeaveCategory category, String detail, LocalDate date) {
        LeaveRequest request = new LeaveRequest();
        request.member = member;
        request.category = category;
        request.detail = detail;
        request.date = date;
        request.status = LeaveStatus.PENDING;
        request.submittedAt = LocalDateTime.now();
        return request;
    }

    public void approve(Member reviewer) {
        validatePending();
        this.status = LeaveStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject(Member reviewer) {
        validatePending();
        this.status = LeaveStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
    }

    private void validatePending() {
        if (status != LeaveStatus.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_REVIEWED);
        }
    }
}
