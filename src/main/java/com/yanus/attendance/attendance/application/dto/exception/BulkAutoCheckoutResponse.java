package com.yanus.attendance.attendance.application.dto.exception;

import java.util.List;

public record BulkAutoCheckoutResponse(
        int processedCount,
        List<Long> updatedIds
) {
}
