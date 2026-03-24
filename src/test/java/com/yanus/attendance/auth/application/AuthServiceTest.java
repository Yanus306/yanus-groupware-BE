package com.yanus.attendance.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.auth.FakeRefreshTokenRepository;
import com.yanus.attendance.auth.infrastructure.JwtTokenProvider;
import com.yanus.attendance.auth.presentation.dto.LoginRequest;
import com.yanus.attendance.auth.presentation.dto.LoginResponse;
import com.yanus.attendance.auth.presentation.dto.RefreshRequest;
import com.yanus.attendance.auth.presentation.dto.RegisterRequest;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.team.FakeTeamRepository;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

    private AuthService authService;
    private FakeMemberRepository memberRepository;
    private FakeRefreshTokenRepository refreshTokenRepository;
    private FakeTeamRepository teamRepository;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        memberRepository = new FakeMemberRepository();
        refreshTokenRepository = new FakeRefreshTokenRepository();
        teamRepository = new FakeTeamRepository();
        jwtTokenProvider = new JwtTokenProvider(
                "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
                3600000L,
                604800000L
        );
        authService = new AuthService(
                memberRepository,
                refreshTokenRepository,
                teamRepository,
                jwtTokenProvider,
                new BCryptPasswordEncoder()
        );
    }

    private Team savedTeam() {
        return teamRepository.save(Team.create(TeamName.BACKEND));
    }

    @Test
    void 회원가입_성공() {
        // given
        Team team = savedTeam();
        RegisterRequest request = new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId());

        // when
        authService.register(request);

        // then
        assertThat(memberRepository.existsByEmail("hong@yanus.com")).isTrue();
    }

    @Test
    void 이메일_중복이면_예외를_던진다() {
        // given
        Team team = savedTeam();
        RegisterRequest request = new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId());
        authService.register(request);

        // when & then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void 로그인_성공() {
        // given
        Team team = savedTeam();
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId()));
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
        Team team = savedTeam();
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId()));
        LoginRequest request = new LoginRequest("hong@yanus.com", "wrongpassword");

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void RefreshToken으로_AccessToken을_재발급한다() {
        // given
        Team team = savedTeam();
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId()));
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
        Team team = savedTeam();
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId()));
        LoginResponse loginResponse = authService.login(new LoginRequest("hong@yanus.com", "password123"));

        // when
        authService.logout(1L);

        // then
        assertThat(refreshTokenRepository.findByToken(loginResponse.refreshToken())).isEmpty();
    }

    @Test
    @DisplayName("비활성화된 멤버가 로그인하면 예외 발생")
    void inactive_member_loging_throw_error() {
        // given
        Team team = savedTeam();
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId()));

        // 비활성화 처리
        Member member = memberRepository.findByEmail("hong@yanus.com").get();
        member.deactivate();

        LoginRequest request = new LoginRequest("hong@yanus.com", "password123");

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_INACTIVE);
    }
}
