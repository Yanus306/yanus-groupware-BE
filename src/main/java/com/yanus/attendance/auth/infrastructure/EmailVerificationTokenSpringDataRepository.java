package com.yanus.attendance.auth.infrastructure;

import com.yanus.attendance.auth.domain.EmailVerificationToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenSpringDataRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);
    void deleteByMemberId(Long memberId);
}
