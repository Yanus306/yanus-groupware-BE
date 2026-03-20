package com.yanus.attendance.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.auth.FakeRefreshTokenRepository;
import com.yanus.attendance.auth.presentation.dto.LoginRequest;
import com.yanus.attendance.auth.presentation.dto.LoginResponse;
import com.yanus.attendance.auth.presentation.dto.RefreshRequest;
import com.yanus.attendance.auth.presentation.dto.RegisterRequest;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import com.yanus.attendance.auth.infrastructure.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

    private AuthService authService;
    private FakeMemberRepository memberRepository;
    private FakeRefreshTokenRepository refreshTokenRepository;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        memberRepository = new FakeMemberRepository();
        refreshTokenRepository = new FakeRefreshTokenRepository();
        jwtTokenProvider = new JwtTokenProvider(
                "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
                3600000L,
                604800000L
        );
        authService = new AuthService(
                memberRepository,
                refreshTokenRepository,
                jwtTokenProvider,
                new BCryptPasswordEncoder()
        );
    }

    @Test
    void 회원가입_성공() {
        // given
        Team team = Team.create(TeamName.BACKEND);
        RegisterRequest request = new RegisterRequest("홍길동", "hong@yanus.com", "password123", team);

        // when
        authService.register(request);

        // then
        assertThat(memberRepository.existsByEmail("hong@yanus.com")).isTrue();
    }

    @Test
    void 이메일_중복이면_예외를_던진다() {
        // given
        Team team = Team.create(TeamName.BACKEND);
        RegisterRequest request = new RegisterRequest("홍길동", "hong@yanus.com", "password123", team);
        authService.register(request);

        // when & then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void 로그인_성공() {
        // given
        Team team = Team.create(TeamName.BACKEND);
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team));
        LoginRequest request = new LoginRequest("hong@yanus.com", "password123");

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void 존재하지_않는_이메일로_로그인하면_예외를_던진다() {
        // given
        LoginRequest request = new LoginRequest("none@yanus.com", "password123");

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 비밀번호가_틀리면_예외를_던진다() {
        // given
        Team team = Team.create(TeamName.BACKEND);
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team));
        LoginRequest request = new LoginRequest("hong@yanus.com", "wrongpassword");

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void RefreshToken으로_AccessToken을_재발급한다() {
        // given
        Team team = Team.create(TeamName.BACKEND);
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team));
        LoginResponse loginResponse = authService.login(new LoginRequest("hong@yanus.com", "password123"));
        RefreshRequest request = new RefreshRequest(loginResponse.refreshToken());

        // when
        LoginResponse response = authService.refresh(request);

        // then
        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    void 만료된_RefreshToken으로_재발급하면_예외를_던진다() {
        // given
        RefreshRequest request = new RefreshRequest("invalid-refresh-token");

        // when & then
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    void 로그아웃_시_RefreshToken이_삭제된다() {
        // given
        Team team = Team.create(TeamName.BACKEND);
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team));
        LoginResponse loginResponse = authService.login(new LoginRequest("hong@yanus.com", "password123"));

        // when
        authService.logout(1L);

        // then
        assertThat(refreshTokenRepository.findByToken(loginResponse.refreshToken())).isEmpty();
    }
}
