package com.yanus.attendance.attendance.presentation.dto;

import java.time.LocalTime;

public record AutoCheckoutTimeRequest(
        LocalTime autoCheckoutTime
) {
}
