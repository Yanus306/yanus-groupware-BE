package com.yanus.attendance.attendance.domain.exception;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceExceptionInputRepository {

    List<AttendanceExceptionInput> findGenerationInputs(LocalDate workDate, DayOfWeek dayOfWeek);
}
