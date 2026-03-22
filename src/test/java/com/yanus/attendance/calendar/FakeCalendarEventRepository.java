package com.yanus.attendance.calendar;

import com.yanus.attendance.calendar.domain.CalendarEvent;
import com.yanus.attendance.calendar.domain.CalendarEventRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeCalendarEventRepository implements CalendarEventRepository {

    private final Map<Long, CalendarEvent> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public CalendarEvent save(CalendarEvent event) {
        if (event.getId() == null) {
            ReflectionTestUtils.setField(event, "id", sequence++);
        }
        store.put(event.getId(), event);
        return event;
    }

    @Override
    public Optional<CalendarEvent> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public List<CalendarEvent> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return store.values().stream()
                .filter(e -> !e.getStartDate().isBefore(startDate))
                .filter(e -> !e.getEndDate().isAfter(endDate))
                .toList();
    }

    @Override
    public List<CalendarEvent> findByCreatedBy(Long memberId) {
        return store.values().stream()
                .filter(e -> e.getCreatedBy().getId().equals(memberId))
                .toList();
    }
}
