package com.yanus.attendance.attendance.infrastructure;

import com.yanus.attendance.attendance.domain.WorkSchedule;
import com.yanus.attendance.attendance.domain.WorkScheduleRepository;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WorkScheduleJpaRepository implements WorkScheduleRepository {

    private final WorkScheduleSpringDataRepository repository;

    @Override
    public WorkSchedule save(WorkSchedule workSchedule) {
        return repository.save(workSchedule);
    }

    @Override
    public Optional<WorkSchedule> findByMemberIdAndDayOfWeek(Long memberId, DayOfWeek dayOfWeek) {
        return repository.findByMemberIdAndDayOfWeek(memberId, dayOfWeek);
    }

    @Override
    public List<WorkSchedule> findAllByMemberId(Long memberId) {
        return repository.findAllByMemberId(memberId);
    }
}
