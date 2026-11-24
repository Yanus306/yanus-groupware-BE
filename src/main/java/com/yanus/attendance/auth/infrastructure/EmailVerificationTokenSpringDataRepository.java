package com.yanus.attendance.auth.infrastructure;

import com.yanus.attendance.auth.domain.EmailVerificationToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenSpringDataRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);
    @Query("select v.memberId from EmailVerificationToken v where v.token = :token")
    Optional<Long> findMemberIdByToken(@Param("token") String token);
    void deleteByMemberId(Long memberId);
}
