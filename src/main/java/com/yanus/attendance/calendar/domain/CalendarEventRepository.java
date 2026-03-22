package com.yanus.attendance.calendar.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository {

    CalendarEvent save(CalendarEvent event);

    Optional<CalendarEvent> findById(Long id);

    void deleteById(Long id);

    List<CalendarEvent> findByDateRange(LocalDate startDate, LocalDate endDate);

    List<CalendarEvent> findByCreatedBy(Long memberId);
}
