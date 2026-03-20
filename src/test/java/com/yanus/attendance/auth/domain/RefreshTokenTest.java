package com.yanus.attendance.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class RefreshTokenTest {

    @Test
    @DisplayName("리프레쉬 토큰 생성")
    void create_refresh_token() {
        // given
        String token = "refresh-token-value";
        Long memberId = 1L;
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        // when
        RefreshToken refreshToken = RefreshToken.create(token, memberId, expiresAt);

        // then
        assertThat(refreshToken.getToken()).isEqualTo(token);
        assertThat(refreshToken.getMemberId()).isEqualTo(memberId);
        assertThat(refreshToken.getExpiresAt()).isEqualTo(expiresAt);
    }
}
