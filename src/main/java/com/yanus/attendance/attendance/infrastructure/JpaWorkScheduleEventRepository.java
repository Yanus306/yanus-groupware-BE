package com.yanus.attendance.attendance.infrastructure;

import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEvent;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEventRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaWorkScheduleEventRepository implements WorkScheduleEventRepository {

    private final WorkScheduleEventSpringDataRepository repository;

    @Override
    public WorkScheduleEvent save(WorkScheduleEvent event) { return repository.save(event); }

    @Override
    public Optional<WorkScheduleEvent> findById(Long id) { return repository.findById(id); }

    @Override
    public Optional<WorkScheduleEvent> findByMemberIdAndDate(Long memberId, LocalDate date) {
        return repository.findByMemberIdAndDate(memberId, date);
    }

    @Override
    public List<WorkScheduleEvent> findAllByMemberIdAndDateBetween(Long memberId, LocalDate start, LocalDate end) {
        return repository.findAllByMemberIdAndDateBetween(memberId, start, end);
    }

    @Override
    public void delete(WorkScheduleEvent event) {
        repository.delete(event);
    }
}
