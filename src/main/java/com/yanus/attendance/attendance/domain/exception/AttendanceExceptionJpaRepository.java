package com.yanus.attendance.attendance.domain.exception;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceExceptionJpaRepository extends JpaRepository<AttendanceException, Long>, AttendanceExceptionRepository {

    @Override
    Optional<AttendanceException> findByMemberIdAndWorkDateAndType(
            Long memberId, LocalDate workDate, AttendanceExceptionType type);

    @Override
    List<AttendanceException> findAllByWorkDate(LocalDate workDate);

    @Override
    List<AttendanceException> findAllByWorkDateAndType(LocalDate workDate, AttendanceExceptionType type);
}
