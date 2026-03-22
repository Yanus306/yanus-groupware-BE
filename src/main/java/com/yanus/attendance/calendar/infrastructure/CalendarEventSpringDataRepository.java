package com.yanus.attendance.calendar.infrastructure;

import com.yanus.attendance.calendar.domain.CalendarEvent;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventSpringDataRepository extends JpaRepository<CalendarEvent, Long> {

    @Query("SELECT e FROM CalendarEvent e WHERE e.startDate >= :startDate AND e.endDate <= :endDate")
    List<CalendarEvent> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    List<CalendarEvent> findAllByCreatedById(Long memberId);
}
