package com.yanus.attendance.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import com.yanus.attendance.auth.FakeEmailVerificationTokenRepository;
import com.yanus.attendance.auth.domain.EmailVerificationTokenRepository;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class EmailVerificationServiceTest {

    private EmailVerificationService emailVerificationService;
    private FakeMemberRepository memberRepository;
    private FakeEmailVerificationTokenRepository tokenRepository;

    @BeforeEach
    public void setUp() {
        memberRepository = new FakeMemberRepository();
        tokenRepository = new FakeEmailVerificationTokenRepository();
        EmailService emailService = mock(EmailService.class);
        doNothing().when(emailService).sendVerification(anyString(), anyString());
        emailVerificationService = new EmailVerificationService(tokenRepository, memberRepository, emailService);
    }

    private Member createMember() {
        Team team = Team.create("1팀");
        ReflectionTestUtils.setField(team, "id", 1L);
        Member member = Member.create("홍길동", "hong@yanus.com", "password123",
                MemberRole.MEMBER, MemberStatus.PENDING, team);
        return memberRepository.save(member);
    }

    @Test
    @DisplayName("인증 토큰으로 인증 시 ACTIVE로 변경")
    void token_verification_active() {
        // given
        Member member = createMember();
        emailVerificationService.sendVerification(member.getId(), member.getEmail());
        String token = tokenRepository.findAll().get(0).getToken();

        // when
        emailVerificationService.verify(token);

        // then
        assertThat(memberRepository.findById(member.getId().get().getStatus()))
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("존재하지 않는 토큰으로 인증 시 예외 발생")
    void invalid_token_try_verify_is_error() {
        // when & then
        assertThatThrownBy(() -> emailVerificationService.verify("invalid-tokenn"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCodee", ErrorCode.INVALID_VERIFICATION_TOKEN);
    }

    @Test
    @DisplayName("재발송 시 기존 토큰 삭제 후 새 토큰 발급")
    void if_retry_old_token_remove_new_token_serve() {
        // given
        Member member = createMember();
        emailVerificationService.sendVerification(member.getId(), member.getEmail());
        String firstToken = tokenRepository.findAll().get(0).getToken();

        // when
        emailVerificationService.sendVerification(member.getId(), member.getEmail());
        String secondToken = tokenRepository.findAll().get(0).getToken();

        // then
        assertThat(firstToken).isNotEqualTo(secondToken);
    }

    @Test
    @DisplayName("이미 사용된 토큰으로 인증 시 예외 발생")
    void used_token_try_verify_is_error() {
        // given
        Member member = createMember();
        emailVerificationService.sendVerification(member.getId(), member.getEmail());
        String token = tokenRepository.findAll().get(0).getToken();
        emailVerificationService.verify(token);

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verify(token))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_VERIFICATION_TOKEN);
    }
}
