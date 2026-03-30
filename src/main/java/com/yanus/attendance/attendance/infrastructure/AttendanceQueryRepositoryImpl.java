package com.yanus.attendance.attendance.infrastructure;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceQueryRepository;
import com.yanus.attendance.attendance.domain.attendance.QAttendance;
import com.yanus.attendance.member.domain.QMember;
import com.yanus.attendance.team.domain.QTeam;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AttendanceQueryRepositoryImpl implements AttendanceQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Attendance> findAllByFilter(LocalDate date, String teamName) {
        QAttendance attendance = QAttendance.attendance;
        QMember member = QMember.member;
        QTeam team = QTeam.team;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(attendance.workDate.eq(date));

        if (teamName != null) {
            builder.and(team.name.eq(teamName));
        }

        return queryFactory
                .selectFrom(attendance)
                .join(attendance.member, member).fetchJoin()
                .join(member.team, team).fetchJoin()
                .where(builder)
                .fetch();
    }
}
