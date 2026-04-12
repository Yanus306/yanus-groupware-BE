package com.yanus.attendance.auth.domain;

public interface EmailVerificationTokenRepository {
    EmailVerificationToken save(EmailVerificationToken token);
    Optional<EmailVerificationToken> findByToken(String token);
    void deleteByMemberId(Long memberId);
}
