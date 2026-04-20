package com.yanus.attendance.member;

import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeMemberRepository implements MemberRepository {

    private final Map<Long, Member> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public Member save(Member member) {
        ReflectionTestUtils.setField(member, "id", sequence++);
        store.put(member.getId(), member);
        return member;
    }

    @Override
    public Optional<Member> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        return store.values().stream()
                .filter(m -> m.getEmail().equals(email))
                .findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        return store.values().stream()
                .anyMatch(m -> m.getEmail().equals(email));
    }

    @Override
    public List<Member> findAllByIds(List<Long> ids) {
        return store.values().stream()
                .filter(m -> ids.contains(m.getId()))
                .toList();
    }

    @Override
    public List<Member> findAll() {
        return store.values().stream().toList();
    }

    @Override
    public List<Member> findAllByTeamName(String teamName) {
        return store.values().stream()
                .filter(m -> m.getTeam() != null)
                .filter(m -> teamName.equals(m.getTeam().getName()))
                .toList();
    }
}
