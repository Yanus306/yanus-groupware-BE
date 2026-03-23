package com.yanus.attendance.calendar.infrastructure;

import com.yanus.attendance.calendar.domain.CalendarEvent;
import com.yanus.attendance.calendar.domain.CalendarEventRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CalendarEventJpaRepository implements CalendarEventRepository {

    private final CalendarEventSpringDataRepository repository;

    @Override
    public CalendarEvent save(CalendarEvent event) {
        return repository.save(event);
    }

    @Override
    public Optional<CalendarEvent> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Override
    public List<CalendarEvent> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return repository.findByDateRange(startDate, endDate);
    }

    @Override
    public List<CalendarEvent> findByCreatedBy(Long memberId) {
        return repository.findAllByCreatedById(memberId);
    }
}
