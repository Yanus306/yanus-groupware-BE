package com.yanus.attendance.auth;

import com.yanus.attendance.auth.domain.EmailVerificationToken;
import com.yanus.attendance.auth.domain.EmailVerificationTokenRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FakeEmailVerificationTokenRepository implements EmailVerificationTokenRepository {

    private final Map<Long, EmailVerificationToken> store = new HashMap<>();
    private long sequence = 1;

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        store.put(sequence++, token);
        return token;
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
