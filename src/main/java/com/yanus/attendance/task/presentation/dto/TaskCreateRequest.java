package com.yanus.attendance.task.presentation.dto;

import com.yanus.attendance.task.domain.TaskPriority;
import java.time.LocalDate;
import java.time.LocalTime;

public record TaskCreateRequest(
        String title,
        LocalDate date,
        LocalTime time,
        TaskPriority priority,
        Long assigneeId,
        boolean isTeamTask
) {
}
