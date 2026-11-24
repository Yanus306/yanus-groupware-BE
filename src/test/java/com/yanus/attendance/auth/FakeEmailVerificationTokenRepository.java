package com.yanus.attendance.auth;

import com.yanus.attendance.auth.domain.EmailVerificationToken;
import com.yanus.attendance.auth.domain.EmailVerificationTokenRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeEmailVerificationTokenRepository implements EmailVerificationTokenRepository {

    private final Map<Long, EmailVerificationToken> store = new HashMap<>();
    private long sequence = 1;

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        ReflectionTestUtils.setField(token, "id", sequence);
        store.put(sequence++, token);
        return token;
    }

    @Override
    public Optional<EmailVerificationToken> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Long> findMemberIdByToken(String token) {
        return findByToken(token).map(EmailVerificationToken::getMemberId);
    }
    @Override
    public Optional<EmailVerificationToken> findByToken(String token) {
        return store.values().stream()
                .filter(t -> t.getToken().equals(token))
                .findFirst();
    }

    @Override
    public void deleteByMemberId(Long memberId) {
        store.values().removeIf(t -> t.getMemberId().equals(memberId));
    }

    public List<EmailVerificationToken> findAll() {
        return new ArrayList<>(store.values());
    }
}
