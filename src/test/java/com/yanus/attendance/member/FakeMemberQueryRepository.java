package com.yanus.attendance.member;

import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberQueryRepository;
import com.yanus.attendance.member.domain.MemberRole;
import java.util.ArrayList;
import java.util.List;

public class FakeMemberQueryRepository implements MemberQueryRepository {

    private final List<Member> store = new ArrayList<>();

    public void save(Member member) {
        store.add(member);
    }

    @Override
    public List<Member> findAllByFilter(String teamName, MemberRole role) {
        return store.stream()
                .filter(m -> teamName == null || m.getTeam().getName().equals(teamName))
                .filter(m -> role == null || m.getRole() == role)
                .toList();
    }
}
