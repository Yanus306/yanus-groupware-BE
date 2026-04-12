package com.yanus.attendance.auth.infrastructure;

import com.yanus.attendance.auth.domain.EmailVerificationToken;
import com.yanus.attendance.auth.domain.EmailVerificationTokenRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EmailVerificationTokenJpaRepository implements EmailVerificationTokenRepository {

    private final EmailVerificationTokenSpringDataRepository repository;

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        return repository.save(token);
    }

    @Override
    public Optional<EmailVerificationToken> findByToken(String token) {
        return repository.findByToken(token);
    }

    @Override
    public void deleteByMemberId(Long memberId) {
        repository.deleteByMemberId(memberId);
    }
}
