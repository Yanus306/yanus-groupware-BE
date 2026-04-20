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

        if (isMissedCheckIn(hasSchedule, hasAttendance)) {
            result.add(AttendanceExceptionType.MISSED_CHECK_IN);
        }

        if (isNoSchedule(hasSchedule, hasAttendance)) {
            result.add(AttendanceExceptionType.NO_SCHEDULE);
        }

        if (isLate(hasSchedule, hasAttendance, attendance, schedule)) {
            result.add(AttendanceExceptionType.LATE);
        }

        return result;
    }

    private boolean isMissedCheckIn(boolean hasSchedule, boolean hasAttendance) {
        return hasSchedule && !hasAttendance;
    }

    private boolean isNoSchedule(boolean hasSchedule, boolean hasAttendance) {
        return !hasSchedule && hasAttendance;
    }

    private boolean isLate(boolean hasSchedule, boolean hasAttendance, Attendance attendance, WorkSchedule schedule) {
        return hasSchedule && hasAttendance
                && attendance.getCheckInTime().toLocalTime().isAfter(schedule.getStartTime());
    }
}
