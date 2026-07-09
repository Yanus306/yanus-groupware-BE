package com.yanus.attendance.task.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TaskCreateCommand(
        String title,
        LocalDate date,
        LocalTime time,
        String priority,
        Long assigneeId,
        boolean isTeamTask,
        List<Long> memberIds
) {
}
