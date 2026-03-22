package com.yanus.attendance.task.infrastructure;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yanus.attendance.task.domain.QTask;
import com.yanus.attendance.task.domain.Task;
import com.yanus.attendance.task.domain.TaskQueryRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TaskQueryRepositoryImpl implements TaskQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Task> findMyTasks(Long memberId, LocalDate startDate, LocalDate endDate) {
        QTask task = QTask.task;
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(task.createdBy.id.eq(memberId));
        builder.and(task.isTeamTask.isFalse());
        if (startDate != null) builder.and(task.date.goe(startDate));
        if (endDate != null) builder.and(task.date.loe(endDate));

        return queryFactory.selectFrom(task)
                .where(builder)
                .fetch();
    }

    @Override
    public List<Task> findTeamTasks(Long teamId, LocalDate startDate, LocalDate endDate) {
        QTask task = QTask.task;
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(task.team.id.eq(teamId));
        builder.and(task.isTeamTask.isTrue());
        if (startDate != null) builder.and(task.date.goe(startDate));
        if (endDate != null) builder.and(task.date.loe(endDate));

        return queryFactory.selectFrom(task)
                .where(builder)
                .fetch();
    }
}
