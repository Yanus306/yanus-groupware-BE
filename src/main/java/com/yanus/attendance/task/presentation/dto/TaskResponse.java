package com.yanus.attendance.task.presentation.dto;

import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.task.domain.Task;
import com.yanus.attendance.task.domain.TaskPriority;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TaskResponse(
        Long id,
        String title,
        LocalDate date,
        LocalTime time,
        TaskPriority priority,
        boolean done,
        boolean isTeamTask,
        Long assigneeId,
        String assigneeName,
        List<Long> memberIds,
        List<String> memberNames
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDate(),
                task.getTime(),
                task.getPriority(),
                task.isDone(),
                task.isTeamTask(),
                task.getAssignee()  != null ? task.getAssignee().getId() : null,
                task.getAssignee() != null ? task.getAssignee().getName() : null,
                task.getMembers().stream().map(Member::getId).toList(),
                task.getMembers().stream().map(Member::getName).toList()
        );
    }
}
