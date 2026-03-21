package com.yanus.attendance.member.infrastructure;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberQueryRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.QMember;
import com.yanus.attendance.team.domain.QTeam;
import com.yanus.attendance.team.domain.TeamName;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberQueryRepositoryImpl implements MemberQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Member> findAllByFilter(TeamName teamName, MemberRole role) {
        return queryFactory
                .selectFrom(QMember.member)
                .join(QMember.member.team, QTeam.team).fetchJoin()
                .where(
                        eqTeamName(teamName),
                        eqRole(role)
                )
                .fetch();
    }

    private BooleanExpression eqTeamName(TeamName teamName) {
        if (teamName == null) {
            return null;
        }
        return QMember.member.team.name.eq(teamName);
    }

    private BooleanExpression eqRole(MemberRole role) {
        if (role == null) {
            return null;
        }
        return QMember.member.role.eq(role);
    }
}
