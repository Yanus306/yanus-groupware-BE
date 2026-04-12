package com.yanus.attendance.attendance.domain.workschedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkScheduleEventRepository {

    WorkScheduleEvent save(WorkScheduleEvent event);

    Optional<WorkScheduleEvent> findById(Long id);

    Optional<WorkScheduleEvent> findByMemberIdAndDate(Long memberId, LocalDate date);

    List<WorkScheduleEvent> findAllByMemberIdAndDateBetween(Long memberId, LocalDate start, LocalDate end);

    void delete(WorkScheduleEvent event);
}
