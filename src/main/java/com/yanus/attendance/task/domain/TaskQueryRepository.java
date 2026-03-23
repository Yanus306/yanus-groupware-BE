package com.yanus.attendance.task.domain;

import java.time.LocalDate;
import java.util.List;

public interface TaskQueryRepository {

    List<Task> findMyTasks(Long memberId, LocalDate startDate, LocalDate endDate);

    List<Task> findTeamTasks(Long teamId, LocalDate startDate, LocalDate endDate);
}
