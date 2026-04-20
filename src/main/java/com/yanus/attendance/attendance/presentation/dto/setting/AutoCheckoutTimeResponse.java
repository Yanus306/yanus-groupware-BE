package com.yanus.attendance.attendance.presentation.dto.setting;

import com.yanus.attendance.attendance.domain.setting.AttendanceSetting;
import java.time.LocalTime;

public record AutoCheckoutTimeResponse(
        LocalTime autoCheckoutTime
) {
    public static AutoCheckoutTimeResponse from(AttendanceSetting setting) {
        return new AutoCheckoutTimeResponse(setting.getAutoCheckoutTime());
    }
}
