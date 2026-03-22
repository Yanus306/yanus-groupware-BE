package com.yanus.attendance.attendance;

import com.yanus.attendance.attendance.domain.Attendance;
import com.yanus.attendance.attendance.domain.AttendanceRepository;
import com.yanus.attendance.team.domain.TeamName;
import java.time.LocalDate;
import java.util.List;

public class FakeAttendanceQueryRepository implements AttendanceRepository{

    private final AttendanceRepository attendanceRepository;

    public FakeAttendanceQueryRepository(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    @Override

    public List<Attendance> findAllByFilter(LocalDate date, TeamName teamName) {
        return attendanceRepository.findAllByWorkDate(date).stream()
                .filter(a -> teamName == null || a.getMember().getTeam().getName() == teamName)
                .toList();
    }
}
