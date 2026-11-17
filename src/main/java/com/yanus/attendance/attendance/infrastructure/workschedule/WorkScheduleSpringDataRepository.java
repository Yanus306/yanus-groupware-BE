package com.yanus.attendance.attendance.infrastructure.workschedule;

import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkScheduleSpringDataRepository extends JpaRepository<WorkSchedule, Long> {

    Optional<WorkSchedule> findByMemberIdAndDayOfWeek(Long memberId, DayOfWeek dayOfWeek);

    List<WorkSchedule> findAllByMemberId(Long memberId);

    @Query("SELECT w FROM WorkSchedule w WHERE w.member.team.id = :teamId")
    List<WorkSchedule> findAllByMemberTeamId(@Param("teamId") Long teamId);

    void deleteByMemberIdAndDayOfWeek(Long memberId, DayOfWeek dayOfWeek);
}
