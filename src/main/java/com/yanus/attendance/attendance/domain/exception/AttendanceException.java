package com.yanus.attendance.attendance.domain.exception;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
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
@Table(name = "attendance_exception")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id")
    private Attendance attendance;

    @Column(nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceExceptionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceExceptionStatus status;

    private String note;
    private String reason;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String resolvedBy;
    private LocalDateTime resolvedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AttendanceException open(
            Member member, Attendance attendance, LocalDate workDate, AttendanceExceptionType type
    ) {
        AttendanceException exception = new AttendanceException();
        exception.member = member;
        exception.attendance = attendance;
        exception.workDate = workDate;
        exception.type = type;
        exception.status = AttendanceExceptionStatus.OPEN;
        exception.createdAt = LocalDateTime.now();
        return exception;
    }

    public void approve(String approvedBy, LocalDateTime approvedAt, String note) {
        ensureStatus(AttendanceExceptionStatus.OPEN);
        this.status = AttendanceExceptionStatus.APPROVED;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        overwriteNote(note);
    }

    public void reject(String note) {
        ensureStatus(AttendanceExceptionStatus.OPEN);
        this.status = AttendanceExceptionStatus.REJECTED;
        overwriteNote(note);
    }

    public void resolve(String resolvedBy, LocalDateTime resolvedAt, String note) {
        ensureNotResolved();
        this.status = AttendanceExceptionStatus.RESOLVED;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        overwriteNote(note);
    }

    private void ensureStatus(AttendanceExceptionStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.INVALID_EXCEPTION_STATE_TRANSITION);
        }
    }

    private void ensureNotResolved() {
        if (this.status == AttendanceExceptionStatus.RESOLVED) {
            throw new BusinessException(ErrorCode.INVALID_EXCEPTION_STATE_TRANSITION);
        }
    }

    private void overwriteNote(String note) {
        if (note == null) {
            return;
        }
        this.note = note;
    }
}
