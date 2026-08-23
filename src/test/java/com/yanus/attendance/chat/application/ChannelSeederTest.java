package com.yanus.attendance.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.chat.FakeChannelMemberRepository;
import com.yanus.attendance.chat.FakeChannelRepository;
import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelType;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.FakeTeamRepository;
import com.yanus.attendance.team.domain.Team;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ChannelSeederTest {

    private ChannelSeeder channelSeeder;
    private FakeChannelRepository channelRepository;
    private FakeChannelMemberRepository channelMemberRepository;
    private FakeMemberRepository memberRepository;
    private FakeTeamRepository teamRepository;

    @BeforeEach
    void setUp() {
        channelRepository = new FakeChannelRepository();
        channelMemberRepository = new FakeChannelMemberRepository();
        memberRepository = new FakeMemberRepository();
        teamRepository = new FakeTeamRepository();
        channelSeeder = new ChannelSeeder(channelRepository, channelMemberRepository, memberRepository);
    }

    @Test
    @DisplayName("기본 채널 5개(General + 팀별 4개)를 생성한다")
    void seeds_default_channels() {
        // when
        channelSeeder.seed();

        // then
        List<Channel> channels = channelRepository.findAll();
        assertThat(channels).hasSize(5);
        assertThat(channels).anyMatch(c -> c.getName().equals("General") && c.getType() == ChannelType.GENERAL);
        assertThat(channels).filteredOn(c -> c.getType() == ChannelType.TEAM).hasSize(4);
    }

    @Test
    @DisplayName("두 번 실행해도 채널이 중복 생성되지 않는다")
    void seeding_is_idempotent() {
        // when
        channelSeeder.seed();
        channelSeeder.seed();

        // then
        assertThat(channelRepository.findAll()).hasSize(5);
    }

    @Test
    @DisplayName("모든 멤버를 General 채널에 참여시킨다")
    void joins_all_members_to_general() {
        // given
        Team team = teamRepository.save(Team.create("기본팀"));
        memberRepository.save(Member.create("A", "a@yanus.com", "enc", MemberRole.MEMBER, MemberStatus.ACTIVE, team));
        memberRepository.save(Member.create("B", "b@yanus.com", "enc", MemberRole.MEMBER, MemberStatus.ACTIVE, team));

        // when
        channelSeeder.seed();
        channelSeeder.seed();

        // then
        assertThat(channelMemberRepository.size()).isEqualTo(2);
    }
}
