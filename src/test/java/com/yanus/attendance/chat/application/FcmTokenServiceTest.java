package com.yanus.attendance.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.chat.FakeDeviceTokenRepository;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.FakeTeamRepository;
import com.yanus.attendance.team.domain.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class FcmTokenServiceTest {

    private FcmTokenService fcmTokenService;
    private FakeDeviceTokenRepository deviceTokenRepository;
    private FakeMemberRepository memberRepository;
    private FakeTeamRepository teamRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        deviceTokenRepository = new FakeDeviceTokenRepository();
        memberRepository = new FakeMemberRepository();
        teamRepository = new FakeTeamRepository();
        fcmTokenService = new FcmTokenService(deviceTokenRepository, memberRepository);

        Team team = teamRepository.save(Team.create("기본팀"));
        member = memberRepository.save(
                Member.create("멤버", "m@yanus.com", "enc", MemberRole.MEMBER, MemberStatus.ACTIVE, team));
    }

    @Test
    @DisplayName("디바이스 토큰을 등록한다")
    void register_token() {
        // when
        fcmTokenService.register(member.getId(), "token-1");

        // then
        assertThat(deviceTokenRepository.findTokensByMemberId(member.getId())).containsExactly("token-1");
    }

    @Test
    @DisplayName("같은 토큰을 중복 등록해도 한 번만 저장된다")
    void register_is_idempotent() {
        // when
        fcmTokenService.register(member.getId(), "token-1");
        fcmTokenService.register(member.getId(), "token-1");

        // then
        assertThat(deviceTokenRepository.findTokensByMemberId(member.getId())).hasSize(1);
    }

    @Test
    @DisplayName("토큰을 해제하면 제거된다")
    void unregister_token() {
        // given
        fcmTokenService.register(member.getId(), "token-1");

        // when
        fcmTokenService.unregister("token-1");

        // then
        assertThat(deviceTokenRepository.findTokensByMemberId(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 멤버가 등록 시 예외 발생")
    void register_member_not_found() {
        // when & then
        assertThatThrownBy(() -> fcmTokenService.register(999L, "token-1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }
}
