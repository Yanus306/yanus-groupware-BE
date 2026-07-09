package com.yanus.attendance.task.application.dto;

import com.yanus.attendance.task.domain.TaskPriority;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TaskUpdateCommand(
        String title,
        LocalDate date,
        LocalTime time,
        TaskPriority priority,
        List<Long> memberIds
) {
}
