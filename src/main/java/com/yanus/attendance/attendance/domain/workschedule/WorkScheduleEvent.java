package com.yanus.attendance.attendance.domain.workschedule;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "work_schedule_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkScheduleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "work_schedule_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    private LocalDate date;

    private LocalTime startTime;

    private LocalTime endTime;

    @Column(name = "ends_next_day", nullable = false)
    private boolean endsNextDay;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static WorkScheduleEvent create(Member member, LocalDate date, LocalTime startTime, LocalTime endTime, boolean endsNextDay) {
        validateTime(startTime, endTime, endsNextDay);
        WorkScheduleEvent event = new WorkScheduleEvent();
        event.member = member;
        event.date = date;
        event.startTime = startTime;
        event.endTime = endTime;
        event.endsNextDay = endsNextDay;
        return event;
    }

    public void update(LocalTime startTime, LocalTime endTime, boolean endsNextDay) {
        validateTime(startTime, endTime, endsNextDay);
        this.startTime = startTime;
        this.endTime = endTime;
        this.endsNextDay = endsNextDay;
    }

    private static void validateTime(LocalTime startTime, LocalTime endTime, boolean endsNextDay) {
        if (endsNextDay) {
            validateOvernight(startTime, endTime);
            return;
        }
        validateDaytime(startTime, endTime);
    }

    private static void validateDaytime(LocalTime startTime, LocalTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.INVALID_WORK_SCHEDULE_TIME);
        }
    }

    private static void validateOvernight(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isAfter(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_OVERNIGHT_WORK_SCHEDULE_TIME);
        }
    }
}
