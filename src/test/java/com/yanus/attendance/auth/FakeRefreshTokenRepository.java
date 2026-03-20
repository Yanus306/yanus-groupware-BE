package com.yanus.attendance.auth;

import com.yanus.attendance.auth.domain.RefreshToken;
import com.yanus.attendance.auth.domain.RefreshTokenRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<Long, RefreshToken> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        ReflectionTestUtils.setField(refreshToken, "id", sequence++);
        store.put(refreshToken.getId(), refreshToken);
        return refreshToken;
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return store.values().stream()
                .filter(rt -> rt.getToken().equals(token))
                .findFirst();
    }

    @Override
    public void deleteByMemberId(Long memberId) {
        store.values().removeIf(rt -> rt.getMemberId().equals(memberId));
    }
}
