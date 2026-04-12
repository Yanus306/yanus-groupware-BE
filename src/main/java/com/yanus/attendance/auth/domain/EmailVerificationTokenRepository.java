package com.yanus.attendance.auth.domain;

import java.util.Optional;

public interface EmailVerificationTokenRepository {
    EmailVerificationToken save(EmailVerificationToken token);
    Optional<EmailVerificationToken> findByToken(String token);
    void deleteByMemberId(Long memberId);
}
