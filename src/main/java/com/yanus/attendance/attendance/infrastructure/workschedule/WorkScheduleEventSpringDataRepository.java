package com.yanus.attendance.attendance.infrastructure.workschedule;

import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkScheduleEventSpringDataRepository extends JpaRepository<WorkScheduleEvent, Long> {

    Optional<WorkScheduleEvent> findByMemberIdAndDate(Long memberId, LocalDate date);

    List<WorkScheduleEvent> findAllByMemberIdAndDateBetween(Long memberId, LocalDate start, LocalDate end);
}

