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
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


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

    public static WorkScheduleEvent create(Member member, LocalDate date, LocalTime startTime, LocalTime endTime) {
        validateTime(startTime, endTime);
        WorkScheduleEvent event = new WorkScheduleEvent();
        event.member = member;
        event.date = date;
        event.startTime = startTime;
        event.endTime = endTime;
        return event;
    }

    public void update(LocalTime startTime, LocalTime endTime) {
        validateTime(startTime, endTime);
        this.startTime = startTime;
        this.endTime = endTime;
    }

    private static void validateTime(LocalTime startTime, LocalTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.INVALID_WORK_SCHEDULE_TIME);
        }
    }
}
