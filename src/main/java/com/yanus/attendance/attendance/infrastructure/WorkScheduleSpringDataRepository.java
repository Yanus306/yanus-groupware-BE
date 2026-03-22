package com.yanus.attendance.attendance.infrastructure;

import com.yanus.attendance.attendance.domain.WorkSchedule;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkScheduleSpringDataRepository extends JpaRepository<WorkSchedule, Long> {

    Optional<WorkSchedule> findByMemberIdAndDayOfWeek(Long memberId, DayOfWeek dayOfWeek);

    List<WorkSchedule> findAllByMemberId(Long memberId);
}
