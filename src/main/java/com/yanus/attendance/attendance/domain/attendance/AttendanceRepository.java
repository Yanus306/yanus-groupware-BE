package com.yanus.attendance.attendance.domain.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository {

    Attendance save(Attendance attendance);

    Optional<Attendance> findByMemberIdAndWorkDate(Long memberId, LocalDate workDate);

    List<Attendance> findAllByMemberId(Long memberId);

    List<Attendance> findAllByWorkDate(LocalDate workDate);

    List<Attendance> findAllByWorkDateAndStatus(LocalDate workDate, AttendanceStatus status);

    void delete(Attendance attendance);
}
