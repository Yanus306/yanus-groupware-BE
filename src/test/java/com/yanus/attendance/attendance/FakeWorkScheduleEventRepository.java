package com.yanus.attendance.attendance;

import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEvent;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEventRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeWorkScheduleEventRepository implements WorkScheduleEventRepository {

    private final Map<Long, WorkScheduleEvent> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public WorkScheduleEvent save(WorkScheduleEvent event) {
        if (event.getId() == null) ReflectionTestUtils.setField(event, "id", sequence++);
        store.put(event.getId(), event);
        return event;
    }

    @Override
    public Optional<WorkScheduleEvent> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<WorkScheduleEvent> findByMemberIdAndDate(Long memberId, LocalDate date) {
        return store.values().stream()
                .filter(e -> e.getMember().getId().equals(memberId) && e.getDate().equals(date))
                .findFirst();
    }

    @Override
    public List<WorkScheduleEvent> findAllByMemberIdAndDateBetween(Long memberId, LocalDate start, LocalDate end) {
        return store.values().stream()
                .filter(e -> e.getMember().getId().equals(memberId))
                .filter(e -> !e.getDate().isBefore(start) && !e.getDate().isAfter(end))
                .toList();
    }

    @Override
    public void delete(WorkScheduleEvent event) {
        store.remove(event.getId());
    }
}

