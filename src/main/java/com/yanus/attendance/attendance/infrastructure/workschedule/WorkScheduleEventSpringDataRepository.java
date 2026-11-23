package com.yanus.attendance.attendance.infrastructure.workschedule;

import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkScheduleEventSpringDataRepository extends JpaRepository<WorkScheduleEvent, Long> {

    Optional<WorkScheduleEvent> findByMemberIdAndDate(Long memberId, LocalDate date);

    @EntityGraph(attributePaths = {"member", "member.team"})
    List<WorkScheduleEvent> findAllByMemberIdAndDateBetween(Long memberId, LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = {"member", "member.team"})
    List<WorkScheduleEvent> findAllByMember_Team_IdAndDateBetween(Long teamId, LocalDate start, LocalDate end);

    @EntityGraph(attributePaths = {"member", "member.team"})
    List<WorkScheduleEvent> findAllByDateBetween(LocalDate start, LocalDate end);
}
