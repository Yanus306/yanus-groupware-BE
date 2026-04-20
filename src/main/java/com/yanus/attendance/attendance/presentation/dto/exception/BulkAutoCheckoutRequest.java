package com.yanus.attendance.attendance.presentation.dto.exception;

import java.time.LocalDate;
import java.util.List;

public record BulkAutoCheckoutRequest(
        LocalDate date,
        List<Long> memberIds
) {
}
