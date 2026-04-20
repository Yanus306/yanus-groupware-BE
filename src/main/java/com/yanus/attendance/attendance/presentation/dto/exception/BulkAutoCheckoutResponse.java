package com.yanus.attendance.attendance.presentation.dto.exception;

import java.util.List;

public record BulkAutoCheckoutResponse(
        int processedCount,
        List<Long> updatedIds
) {
}
