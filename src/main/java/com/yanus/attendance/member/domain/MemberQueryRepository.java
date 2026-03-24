package com.yanus.attendance.member.domain;

import java.util.List;

public interface MemberQueryRepository {

    List<Member> findAllByFilter(String teamName, MemberRole role);
}
