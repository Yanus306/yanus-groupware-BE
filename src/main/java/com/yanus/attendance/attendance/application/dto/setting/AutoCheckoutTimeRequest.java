package com.yanus.attendance.attendance.application.dto.setting;

import java.time.LocalTime;

public record AutoCheckoutTimeRequest(
        LocalTime autoCheckoutTime
) {
}
