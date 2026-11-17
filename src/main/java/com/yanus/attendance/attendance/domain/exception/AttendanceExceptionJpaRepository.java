package com.yanus.attendance.attendance.domain.exception;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceExceptionJpaRepository extends JpaRepository<AttendanceException, Long>,
        AttendanceExceptionRepository, AttendanceExceptionInputRepository {

    @Override
    Optional<AttendanceException> findByMemberIdAndWorkDateAndType(
            Long memberId, LocalDate workDate, AttendanceExceptionType type);

    @Override
    List<AttendanceException> findAllByWorkDate(LocalDate workDate);

    @Override
    @Query("""
            select new com.yanus.attendance.attendance.domain.exception.AttendanceExceptionInput(
                m, weekly, event, attendance, existing.type)
            from Member m
            left join WorkSchedule weekly on weekly.member = m and weekly.dayOfWeek = :dayOfWeek
            left join WorkScheduleEvent event on event.member = m and event.date = :workDate
            left join Attendance attendance on attendance.member = m and attendance.workDate = :workDate
            left join AttendanceException existing on existing.member = m and existing.workDate = :workDate
            """)
    List<AttendanceExceptionInput> findGenerationInputs(@Param("workDate") LocalDate workDate,
                                                        @Param("dayOfWeek") DayOfWeek dayOfWeek);

    @Override
    @Query("""
            select e from AttendanceException e
            join fetch e.member m
            join fetch m.team
            left join fetch e.attendance
            where e.workDate = :workDate
            """)
    List<AttendanceException> findAllByWorkDateWithDetails(@Param("workDate") LocalDate workDate);

    @Override
    List<AttendanceException> findAllByWorkDateAndType(LocalDate workDate, AttendanceExceptionType type);

    @Override
    void deleteAllByAttendanceId(Long attendanceId);
}
