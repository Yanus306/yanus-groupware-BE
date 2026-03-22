package com.yanus.attendance.task;

import com.yanus.attendance.task.domain.Task;
import com.yanus.attendance.task.domain.TaskQueryRepository;
import com.yanus.attendance.task.domain.TaskRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class FakeTaskQueryRepository implements TaskQueryRepository {

    private final List<Task> store = new ArrayList<>();

    public void add(Task task) {
        store.add(task);
    }

    @Override
    public List<Task> findMyTasks(Long memberId, LocalDate startDate, LocalDate endDate) {
        return store.stream()
                .filter(t -> t.getCreatedBy().getId().equals(memberId))
                .filter(t -> !t.getDate().isBefore(startDate))
                .filter(t -> !t.getDate().isAfter(endDate))
                .toList();
    }

    @Override
    public List<Task> findTeamTasks(Long teamId, LocalDate startDate, LocalDate endDate) {
        return store.stream()
                .filter(t -> t.isTeamTask() && t.getTeam().getId().equals(teamId))
                .filter(t -> !t.getDate().isBefore(startDate))
                .filter(t -> !t.getDate().isAfter(endDate))
                .toList();
    }
}
