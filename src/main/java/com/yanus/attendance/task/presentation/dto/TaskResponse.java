package com.yanus.attendance.task.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TaskResponse(
        Long id,
        String title,
        LocalDate date,
        LocalTime time,
        String priority,
        boolean done,
        boolean isTeamTask,
        Long assigneeId,
        String assigneeName,
        List<Long> memberIds,
        List<String> memberNames
) {
    public static TaskResponse from(com.yanus.attendance.task.application.dto.TaskResponse response) {
        return new TaskResponse(
                response.id(),
                response.title(),
                response.date(),
                response.time(),
                response.priority().name(),
                response.done(),
                response.isTeamTask(),
                response.assigneeId(),
                response.assigneeName(),
                response.memberIds(),
                response.memberNames()
        );
    }
}
