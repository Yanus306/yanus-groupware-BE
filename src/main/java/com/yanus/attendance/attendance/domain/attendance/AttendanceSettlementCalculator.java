package com.yanus.attendance.attendance.domain.attendance;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public class AttendanceSettlementCalculator {

    private static final int FEE_PER_MINUTE = 100;

    public static int calculateLateMinutes(LocalTime scheduledStart, LocalDateTime checkIn) {
        LocalTime checkInTime = checkIn.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        LocalTime scheduled = scheduledStart.truncatedTo(ChronoUnit.MINUTES);
        int diff = (int) ChronoUnit.MINUTES.between(scheduled, checkInTime);
        return Math.max(0, diff);
    }

    public static int calculateFee(int lateMinutes) {
        return lateMinutes * FEE_PER_MINUTE;
    }
}
