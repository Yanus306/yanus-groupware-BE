package com.yanus.attendance.task.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TaskUpdateCommand(
        String title,
        LocalDate date,
        LocalTime time,
        String priority,
        List<Long> memberIds
) {
}
