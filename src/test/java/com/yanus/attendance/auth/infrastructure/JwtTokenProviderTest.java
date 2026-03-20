package com.yanus.attendance.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
                3600000L,
                604800000L
        );
    }

    @Test
    @DisplayName("Access Token 생성")
    void create_access_token() {
        // given
        Long memberId = 1L;
        MemberRole role = MemberRole.MEMBER;

        // when
        String token = jwtTokenProvider.createAccessToken(memberId, role);

        //then
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("Access Token 멤버ID를 추출")
    void getMemberId() {
        // given
        Long memberId = 1L;
        String token = jwtTokenProvider.createAccessToken(memberId, MemberRole.MEMBER);

        // when
        Long result = jwtTokenProvider.getMemberId(token);

        // then
        assertThat(result).isEqualTo(memberId);
    }

    @Test
    @DisplayName("Access Token 역할 추출")
    void getRole() {
        // given
        String token = jwtTokenProvider.createAccessToken(1L, MemberRole.ADMIN);

        // when
        String role = jwtTokenProvider.getRole(token);

        // then
        assertThat(role).isEqualTo("ADMIN");
    }

}
