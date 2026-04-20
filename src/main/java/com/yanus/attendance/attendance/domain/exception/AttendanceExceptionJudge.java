package com.yanus.attendance.attendance.domain.exception;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import java.util.ArrayList;
import java.util.List;

public class AttendanceExceptionJudge {

    public List<AttendanceExceptionType> judge(
            WorkSchedule schedule,
            Attendance attendance,
            boolean missedCheckoutThresholdPassed
    ) {
        List<AttendanceExceptionType> result = new ArrayList<>();

        boolean hasSchedule = schedule != null;
        boolean hasAttendance = attendance != null;

        if (hasSchedule && !hasAttendance) {
            result.add(AttendanceExceptionType.MISSED_CHECK_IN);
        }
        return result;
    }
}
