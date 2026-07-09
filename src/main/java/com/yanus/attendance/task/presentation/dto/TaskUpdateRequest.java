package com.yanus.attendance.task.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TaskUpdateRequest(
        String title,
        LocalDate date,
        LocalTime time,
        String priority,
        List<Long> memberIds
) {
}
