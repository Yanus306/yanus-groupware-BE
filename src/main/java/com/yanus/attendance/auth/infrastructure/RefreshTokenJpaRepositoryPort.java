package com.yanus.attendance.auth.infrastructure;

import com.yanus.attendance.auth.domain.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenJpaRepositoryPort extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByMemberId(Long memberId);
}
