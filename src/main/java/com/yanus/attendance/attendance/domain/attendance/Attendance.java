package com.yanus.attendance.attendance.domain.attendance;

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
@Table(name = "attendance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    private LocalDate workDate;

    private LocalDateTime checkInTime;

    private LocalDateTime checkOutTime;

    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;

    public static Attendance checkIn(Member member, LocalDateTime checkInTime) {
        Attendance attendance = new Attendance();
        attendance.member = member;
        attendance.workDate = checkInTime.toLocalDate();
        attendance.checkInTime = checkInTime;
        attendance.status = AttendanceStatus.WORKING;
        return attendance;
    }

    public void checkOut(LocalDateTime checkOutTime) {
        validateTwiceCheckOut();
        validateCheckOutTime(checkOutTime);
        this.checkOutTime = checkOutTime;
        this.status = AttendanceStatus.LEFT;
    }

    private void validateTwiceCheckOut() {
        if (status == AttendanceStatus.LEFT) {
            throw new BusinessException(ErrorCode.ALREADY_CHECKED_OUT);
        }
    }

    private void validateCheckOutTime(LocalDateTime checkOutTime) {
        if (checkOutTime.isBefore(checkInTime)) {
            throw new BusinessException(ErrorCode.INVALID_CHECKOUT_TIME);
        }
    }
}
