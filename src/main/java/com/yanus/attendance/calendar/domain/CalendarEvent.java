package com.yanus.attendance.calendar.domain;

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

@Entity
@Table(name = "calendar_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calendar_event_id")
    private Long id;

    private String title;

    private LocalDate startDate;

    private LocalTime startTime;

    private LocalDate endDate;

    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Member createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static CalendarEvent create(Member createdBy, String title,
                                       LocalDate startDate, LocalTime startTime,
                                       LocalDate endDate, LocalTime endTime) {
        validateEndTime(startDate, startTime, endDate, endTime);
        CalendarEvent event = new CalendarEvent();
        event.createdBy = createdBy;
        event.title = title;
        event.startDate = startDate;
        event.startTime = startTime;
        event.endDate = endDate;
        event.endTime = endTime;
        event.createdAt = LocalDateTime.now();
        event.updatedAt = LocalDateTime.now();
        return event;
    }

    public void update(String title,
                       LocalDate startDate, LocalTime startTime,
                       LocalDate endDate, LocalTime endTime) {
        validateEndTime(startDate, startTime, endDate, endTime);
        this.title = title;
        this.startDate = startDate;
        this.startTime = startTime;
        this.endDate = endDate;
        this.endTime = endTime;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateEndTime(LocalDate startDate, LocalTime startTime,
                                        LocalDate endDate, LocalTime endTime) {
        LocalDateTime start = LocalDateTime.of(startDate, startTime);
        LocalDateTime end = LocalDateTime.of(endDate, endTime);
        if (!end.isAfter(start)) {
            throw new BusinessException(ErrorCode.INVALID_CALENDAR_END_TIME);
        }
    }
}
