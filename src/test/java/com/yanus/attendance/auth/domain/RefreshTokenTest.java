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

    @Test
    @DisplayName("만료되지 않은 토큰은 false를 반환")
    void not_expired_token_return_false() {
        // given
        RefreshToken refreshToken = RefreshToken.create(
                "token", 1L, LocalDateTime.now().plusDays(7)
        );

        //when
        boolean result = refreshToken.isExpired();

        //then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 true를 반환")
    void expired_token_return_true() {
        // given
        RefreshToken refreshToken = RefreshToken.create(
                "token", 1L, LocalDateTime.now().minusSeconds(1)
        );

        //when
        boolean result = refreshToken.isExpired();

        //then
        assertThat(result).isTrue();
    }
}
