package com.yanus.attendance.auth.infrastructure;

import com.yanus.attendance.auth.domain.RefreshToken;
import com.yanus.attendance.auth.domain.RefreshTokenRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenJpaRepository implements RefreshTokenRepository {

    private final RefreshTokenJpaRepositoryPort port;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return port.save(refreshToken);
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return port.findByToken(token);
    }

    @Override
    public void deleteByMemberId(Long memberId) {
        port.deleteByMemberId(memberId);
    }
}

