package com.yanus.attendance.attendance.domain.exception;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceExceptionRepository {
    AttendanceException save(AttendanceException attendanceException);
    Optional<AttendanceException> findById(Long id);
    Optional<AttendanceException> findByMemberIdAndWorkDateAndType(
            Long memberId, LocalDate workDate, AttendanceExceptionType type
    );
    List<AttendanceException> findAllByWorkDate(LocalDate workDate);
}
