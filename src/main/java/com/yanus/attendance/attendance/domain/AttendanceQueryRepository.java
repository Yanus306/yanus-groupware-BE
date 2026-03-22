package com.yanus.attendance.attendance.domain;

import com.yanus.attendance.team.domain.TeamName;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceQueryRepository {
    List<Attendance> findAllByFilter(LocalDate date, TeamName teamName);
}
