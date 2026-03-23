package com.yanus.attendance.task.presentation.dto;

import com.yanus.attendance.task.domain.TaskPriority;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TaskCreateRequest(
        String title,
        LocalDate date,
        LocalTime time,
        TaskPriority priority,
        Long assigneeId,
        boolean isTeamTask,
        List<Long> memberIds
) {
}
