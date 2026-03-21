package com.yanus.attendance.member.domain;

import com.yanus.attendance.team.domain.TeamName;
import java.util.List;

public interface MemberQueryRepository {

    List<Member> findAllByFilter(TeamName teamName, MemberRole role);
}
