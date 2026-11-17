package com.yanus.attendance.attendance.domain.exception;

import java.util.List;

public interface AttendanceExceptionInsertRepository {

    void insertMissing(List<AttendanceException> candidates);
}
