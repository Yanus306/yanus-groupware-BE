package com.yanus.attendance.chat.application;

import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelMember;
import com.yanus.attendance.chat.domain.ChannelMemberRepository;
import com.yanus.attendance.chat.domain.ChannelRepository;
import com.yanus.attendance.chat.domain.ChannelType;
import com.yanus.attendance.member.domain.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 애플리케이션 기동 시 기본 채널(General + 팀별)을 멱등하게 생성하고,
 * 모든 멤버를 General 채널에 참여시킨다.
 */
@Component
@RequiredArgsConstructor
public class ChannelSeeder implements ApplicationRunner {

    private static final String GENERAL_CHANNEL = "General";
    private static final List<String> TEAM_CHANNELS = List.of("DEV", "DESIGN", "MARKETING", "PRODUCT");

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        Channel general = ensureChannel(GENERAL_CHANNEL, ChannelType.GENERAL);
        TEAM_CHANNELS.forEach(name -> ensureChannel(name, ChannelType.TEAM));
        joinAllMembersToGeneral(general);
    }

    private Channel ensureChannel(String name, ChannelType type) {
        return channelRepository.findByName(name)
                .orElseGet(() -> channelRepository.save(Channel.create(name, type)));
    }

    private void joinAllMembersToGeneral(Channel general) {
        memberRepository.findAll().forEach(member -> {
            if (!channelMemberRepository.existsByChannelIdAndMemberId(general.getId(), member.getId())) {
                channelMemberRepository.save(ChannelMember.create(general, member));
            }
        });
    }
}
