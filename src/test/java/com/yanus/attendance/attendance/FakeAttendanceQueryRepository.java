package com.yanus.attendance.attendance;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceQueryRepository;
import com.yanus.attendance.attendance.domain.attendance.AttendanceRepository;
import java.time.LocalDate;
import java.util.List;

public class FakeAttendanceQueryRepository implements AttendanceQueryRepository {

    private final AttendanceRepository attendanceRepository;

    public FakeAttendanceQueryRepository(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    @Override

    public List<Attendance> findAllByFilter(LocalDate date, String teamName) {
        return attendanceRepository.findAllByWorkDate(date).stream()
                .filter(a -> teamName == null || a.getMember().getTeam().getName().equals(teamName))
                .toList();
    }
}
