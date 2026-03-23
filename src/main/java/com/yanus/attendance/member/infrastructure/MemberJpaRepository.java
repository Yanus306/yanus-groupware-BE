package com.yanus.attendance.member.infrastructure;

import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberJpaRepository implements MemberRepository {

    private final MemberJpaRepositoryPort port;

    @Override
    public Member save(Member member) {
        return port.save(member);
    }

    @Override
    public Optional<Member> findById(Long id) {
        return port.findById(id);
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        return port.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return port.existsByEmail(email);
    }

    @Override
    public List<Member> findAllByIds(List<Long> ids) {
        return port.findAllByIdIn(ids);
    }

}

interface MemberJpaRepositoryPort extends org.springframework.data.jpa.repository.JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Member> findAllByIdIn(List<Long> ids);
}
