package com.yanus.attendance.attendance.domain;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceQueryRepository {
    List<Attendance> findAllByFilter(LocalDate date, String teamName);
}
