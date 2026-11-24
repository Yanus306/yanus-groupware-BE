package com.yanus.attendance.member.infrastructure;

import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    public Optional<Member> findByIdForUpdate(Long id) {
        return port.findByIdForUpdate(id);
    }

    @Override
    public Optional<Member> findByEmailForUpdate(String email) {
        return port.findByEmailForUpdate(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return port.existsByEmail(email);
    }

    @Override
    public List<Member> findAllByIds(List<Long> ids) {
        return port.findAllByIdIn(ids);
    }

    @Override
    public List<Member> findAll() {
        return port.findAll();
    }

    @Override
    public List<Member> findAllByTeamName(String teamName) {
        return port.findAllByTeamName(teamName);
    }

}

interface MemberJpaRepositoryPort extends org.springframework.data.jpa.repository.JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.email = :email")
    Optional<Member> findByEmailForUpdate(@Param("email") String email);
    boolean existsByEmail(String email);
    List<Member> findAllByIdIn(List<Long> ids);
    List<Member> findAllByTeamName(String teamName);
}
