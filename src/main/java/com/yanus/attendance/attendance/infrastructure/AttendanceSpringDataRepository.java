package com.yanus.attendance.attendance.infrastructure;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSpringDataRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByMemberIdAndWorkDate(Long memberId, LocalDate workDate);

    List<Attendance> findAllByMemberId(Long memberId);

    List<Attendance> findAllByWorkDate(LocalDate workDate);

    List<Attendance> findAllByWorkDateAndStatus(LocalDate workDate, AttendanceStatus status);
}
