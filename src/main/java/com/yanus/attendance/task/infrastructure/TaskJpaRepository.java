package com.yanus.attendance.task.infrastructure;

import com.yanus.attendance.task.domain.Task;
import com.yanus.attendance.task.domain.TaskRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TaskJpaRepository implements TaskRepository {

    private final TaskSpringDataRepository repository;

    @Override
    public Task save(Task task) {
        return repository.save(task);
    }

    @Override
    public Optional<Task> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
