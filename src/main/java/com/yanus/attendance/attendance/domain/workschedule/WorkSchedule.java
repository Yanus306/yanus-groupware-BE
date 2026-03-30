package com.yanus.attendance.attendance.domain.workschedule;

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
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "work_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "work_schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "week_pattern", nullable = false)
    private WeekPattern weekPattern;

    public static WorkSchedule create(Member member, DayOfWeek dayOfWeek,
                                      LocalTime startTime, LocalTime endTime, WeekPattern weekPattern) {
        validateTime(startTime, endTime);
        WorkSchedule schedule = new WorkSchedule();
        schedule.member = member;
        schedule.dayOfWeek = dayOfWeek;
        schedule.startTime = startTime;
        schedule.endTime = endTime;
        schedule.weekPattern = weekPattern != null ? weekPattern : WeekPattern.EVERY;
        return schedule;
    }

    public void update(LocalTime startTime, LocalTime endTime) {
        validateTime(startTime, endTime);
        this.startTime = startTime;
        this.endTime = endTime;
    }

    private static void validateTime(LocalTime stateTime, LocalTime endTime) {
        if (!endTime.isAfter(stateTime)) {
            throw new BusinessException(ErrorCode.INVALID_WORK_SCHEDULE_TIME);
        }
    }
}
