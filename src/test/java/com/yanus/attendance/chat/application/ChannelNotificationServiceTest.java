package com.yanus.attendance.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.chat.FakeChannelMuteRepository;
import com.yanus.attendance.chat.FakeChannelRepository;
import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelType;
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

public class ChannelNotificationServiceTest {

    private ChannelNotificationService service;
    private FakeChannelMuteRepository muteRepository;
    private FakeChannelRepository channelRepository;
    private FakeMemberRepository memberRepository;
    private FakeTeamRepository teamRepository;

    private Channel channel;
    private Member member;

    @BeforeEach
    void setUp() {
        muteRepository = new FakeChannelMuteRepository();
        channelRepository = new FakeChannelRepository();
        memberRepository = new FakeMemberRepository();
        teamRepository = new FakeTeamRepository();
        service = new ChannelNotificationService(muteRepository, channelRepository, memberRepository);

        channel = channelRepository.save(Channel.create("General", ChannelType.GENERAL));
        Team team = teamRepository.save(Team.create("기본팀"));
        member = memberRepository.save(
                Member.create("멤버", "m@yanus.com", "enc", MemberRole.MEMBER, MemberStatus.ACTIVE, team));
    }

    @Test
    @DisplayName("채널 알림을 끄면 음소거 목록에 포함된다")
    void mute_channel() {
        // when
        service.setMuted(member.getId(), channel.getId(), true);

        // then
        assertThat(service.isMuted(member.getId(), channel.getId())).isTrue();
        assertThat(service.getMutedChannelIds(member.getId())).containsExactly(channel.getId());
    }

    @Test
    @DisplayName("알림을 다시 켜면 음소거가 해제된다")
    void unmute_channel() {
        // given
        service.setMuted(member.getId(), channel.getId(), true);

        // when
        service.setMuted(member.getId(), channel.getId(), false);

        // then
        assertThat(service.isMuted(member.getId(), channel.getId())).isFalse();
        assertThat(service.getMutedChannelIds(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("음소거를 두 번 설정해도 중복 저장되지 않는다")
    void mute_is_idempotent() {
        // when
        service.setMuted(member.getId(), channel.getId(), true);
        service.setMuted(member.getId(), channel.getId(), true);

        // then
        assertThat(service.getMutedChannelIds(member.getId())).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 채널 설정 시 예외 발생")
    void mute_channel_not_found() {
        // when & then
        assertThatThrownBy(() -> service.setMuted(member.getId(), 999L, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHANNEL_NOT_FOUND);
    }
}
