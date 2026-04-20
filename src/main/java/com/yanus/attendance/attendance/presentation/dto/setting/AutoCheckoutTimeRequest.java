package com.yanus.attendance.attendance.presentation.dto.setting;

import java.time.LocalTime;

public record AutoCheckoutTimeRequest(
        LocalTime autoCheckoutTime
) {
}
