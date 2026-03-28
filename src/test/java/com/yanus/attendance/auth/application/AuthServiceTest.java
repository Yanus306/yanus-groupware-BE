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
        return teamRepository.save(Team.create("1팀"));
    }

    @Test
    @DisplayName("회원가입 성공")
    void register_success() {
        // given
        Team team = savedTeam();
        RegisterRequest request = new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId());

        // when
        authService.register(request);

        // then
        assertThat(memberRepository.existsByEmail("hong@yanus.com")).isTrue();
    }

    @Test
    @DisplayName("이메일 중복 시 예외 발생")
    void duplicate_email_throw_error() {
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
    @DisplayName("로그인 성공")
    void login_success() {
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
    @DisplayName("존재하지 않는 이메일로 로그인 시 예외 발생")
    void not_exist_email_throw_error() {
        // given
        LoginRequest request = new LoginRequest("none@yanus.com", "password123");

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 예외 발생")
    void incorrect_password_throw_error() {
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
    @DisplayName("리프레쉬 토큰으로 엑세스 토큰을 재발급")
    void refresh_token_reissue_access_token() {
        // given
        Team team = savedTeam();
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId()));
        LoginResponse loginResponse = authService.login(new LoginRequest("hong@yanus.com", "password123"));
        RefreshRequest request = new RefreshRequest(loginResponse.refreshToken());

        // when
        LoginResponse response = authService.refresh(request);

        // then
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotEqualTo(loginResponse.refreshToken());
    }

    @Test
    @DisplayName("만료된 리프레쉬 토큰으로 재발급 시 예외 발생")
    void invalid_refresh_token_throw_error() {
        // given
        RefreshRequest request = new RefreshRequest("invalid-refresh-token");

        // when & then
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("로그아웃 시 리프레쉬 토큰 삭제")
    void logout_removes_refresh_token() {
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
        Member member = memberRepository.findByEmail("hong@yanus.com").get();
        member.deactivate();
        LoginRequest request = new LoginRequest("hong@yanus.com", "password123");

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_INACTIVE);
    }

    @Test
    @DisplayName("리프레시 토큰 재발급 시 새 토큰 발급")
    void refresh_token_reissue_new_token() {
        // given
        Team team = savedTeam();
        authService.register(new RegisterRequest("김인직", "asdasd@naver.com", "asdasd12", team.getId()));
        LoginResponse loginResponse = authService.login(new LoginRequest("asdasd@naver.com", "asdasd12"));
        String oldRefreshToken = loginResponse.refreshToken();

        // when
        LoginResponse response = authService.refresh(new RefreshRequest(oldRefreshToken));

        // then
        assertThat(response.refreshToken()).isNotEqualTo(oldRefreshToken);
        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("사용된 리프레시 토큰 재사용 시 예외 발생")
    void used_refresh_token_throw_error() {
        // given
        Team team = savedTeam();
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId()));
        LoginResponse loginResponse = authService.login(new LoginRequest("hong@yanus.com", "password123"));
        String oldRefreshToken = loginResponse.refreshToken();
        authService.refresh(new RefreshRequest(oldRefreshToken)); // 1회 사용

        // when & then
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(oldRefreshToken)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOKEN_REUSED);
    }

    @Test
    @DisplayName("재사용 감지 시 해당 계정 전체 토큰 무효화")
    void reissue_token_invalidates_account_tokens() {
        // given
        Team team = savedTeam();
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId()));
        LoginResponse loginResponse = authService.login(new LoginRequest("hong@yanus.com", "password123"));
        String oldRefreshToken = loginResponse.refreshToken();
        LoginResponse rotated = authService.refresh(new RefreshRequest(oldRefreshToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(oldRefreshToken)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOKEN_REUSED);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rotated.refreshToken())))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("로그인 5회 실패 시 계정이 잠김")
    void 로그인_5회_실패_시_계정가_잠긴다() {
        // given
        Team team = savedTeam();
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId()));

        // when
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("hong@yanus.com", "wrongpassword")))
                    .isInstanceOf(BusinessException.class);
        }

        // then
        assertThatThrownBy(() -> authService.login(new LoginRequest("hong@yanus.com", "password123")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("로그인 성공 시 실패 횟수가 초기화")
    void login_success_resets_failed_attempts() {
        // given
        Team team = savedTeam();
        authService.register(new RegisterRequest("홍길동", "hong@yanus.com", "password123", team.getId()));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("hong@yanus.com", "wrongpassword")))
                    .isInstanceOf(BusinessException.class);
        }
        authService.login(new LoginRequest("hong@yanus.com", "password123"));

        // when
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("hong@yanus.com", "wrongpassword")))
                    .isInstanceOf(BusinessException.class);
        }

        // then
        assertThatThrownBy(() -> authService.login(new LoginRequest("hong@yanus.com", "wrongpassword")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_LOCKED);
    }
}
