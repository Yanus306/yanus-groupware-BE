package com.yanus.attendance.attendance.domain.exception;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceExceptionJpaRepository extends JpaRepository<AttendanceException, Long>, AttendanceExceptionRepository {

    @Override
    Optional<AttendanceException> findByMemberIdAndWorkDateAndType(
            Long memberId, LocalDate workDate, AttendanceExceptionType type);

    @Override
    List<AttendanceException> findAllByWorkDate(LocalDate workDate);

    @Override
    @Query("""
            select new com.yanus.attendance.attendance.domain.exception.AttendanceExceptionKey(e.member.id, e.type)
            from AttendanceException e where e.workDate = :workDate
            """)
    List<AttendanceExceptionKey> findKeysByWorkDate(@Param("workDate") LocalDate workDate);

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
