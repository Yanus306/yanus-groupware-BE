package com.yanus.attendance.task.domain;

import java.util.Optional;

public interface TaskRepository {

    Task save(Task task);

    Optional<Task> findById(Long id);

    void deleteById(Long id);
}
