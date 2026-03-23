package com.yanus.attendance.member.domain;

import java.util.List;
import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Member> findAllByIds(List<Long> ids);
}
