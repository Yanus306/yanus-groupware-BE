package com.yanus.attendance.task.infrastructure;

import com.yanus.attendance.task.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskSpringDataRepository extends JpaRepository<Task, Long> {
}
