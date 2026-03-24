package com.yanus.attendance.attendance.domain;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface WorkScheduleRepository {

    WorkSchedule save(WorkSchedule workSchedule);

    Optional<WorkSchedule> findByMemberIdAndDayOfWeek(Long memberId, DayOfWeek dayOfWeek);

    List<WorkSchedule> findAllByMemberId(Long memberId);

    void deleteByMemberIdAndDayOfWeek(Long memberId, DayOfWeek dayOfWeek);
}
