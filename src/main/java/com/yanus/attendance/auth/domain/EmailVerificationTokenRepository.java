package com.yanus.attendance.auth.domain;

import java.util.Optional;

public interface EmailVerificationTokenRepository {
    EmailVerificationToken save(EmailVerificationToken token);
    Optional<EmailVerificationToken> findById(Long id);
    Optional<EmailVerificationToken> findByToken(String token);
    Optional<Long> findMemberIdByToken(String token);
    void deleteByMemberId(Long memberId);
}
