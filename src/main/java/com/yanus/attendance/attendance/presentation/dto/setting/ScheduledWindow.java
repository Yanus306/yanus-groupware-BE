package com.yanus.attendance.attendance.presentation.dto.setting;

import java.time.LocalTime;

public record ScheduledWindow(
        LocalTime start,
        LocalTime end,
        boolean endsNextDay
) {
}
