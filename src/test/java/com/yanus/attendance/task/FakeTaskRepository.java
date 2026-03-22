package com.yanus.attendance.task;

import com.yanus.attendance.task.domain.Task;
import com.yanus.attendance.task.domain.TaskRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeTaskRepository implements TaskRepository {

    private final Map<Long, Task> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public Task save(Task task) {
        if (task.getId() == null) {
            ReflectionTestUtils.setField(task, "id", sequence++);
        }
        store.put(task.getId(), task);
        return task;
    }

    @Override
    public Optional<Task> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}
