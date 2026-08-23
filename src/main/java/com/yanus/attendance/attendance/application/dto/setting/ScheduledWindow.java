package com.yanus.attendance.attendance.application.dto.setting;

import java.time.LocalTime;

public record ScheduledWindow(
        LocalTime start,
        LocalTime end,
        boolean endsNextDay
) {
}
