package com.yanus.attendance.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.chat.FakeChannelMemberRepository;
import com.yanus.attendance.chat.FakeChannelMuteRepository;
import com.yanus.attendance.chat.FakeChannelRepository;
import com.yanus.attendance.chat.FakeDeviceTokenRepository;
import com.yanus.attendance.chat.application.event.NewMessageEvent;
import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelMember;
import com.yanus.attendance.chat.domain.ChannelMute;
import com.yanus.attendance.chat.domain.ChannelType;
import com.yanus.attendance.chat.domain.DeviceToken;
import com.yanus.attendance.chat.domain.MessageType;
import com.yanus.attendance.chat.infrastructure.ChatSseService;
import com.yanus.attendance.chat.presentation.dto.MessageResponse;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.FakeTeamRepository;
import com.yanus.attendance.team.domain.Team;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ChatNotificationListenerTest {

    private ChatNotificationListener listener;
    private ChatSseService sseService;
    private FakeChannelMemberRepository channelMemberRepository;
    private FakeChannelMuteRepository channelMuteRepository;
    private FakeDeviceTokenRepository deviceTokenRepository;
    private CapturingFcmSender fcmSender;

    private FakeChannelRepository channelRepository;
    private FakeMemberRepository memberRepository;
    private FakeTeamRepository teamRepository;

    private Channel channel;
    private Member sender;
    private Member online;
    private Member offlineUnmuted;
    private Member offlineMuted;

    static class CapturingFcmSender implements FcmSender {
        List<String> tokens = new ArrayList<>();

        @Override
        public void sendToTokens(List<String> tokens, String title, String body, Map<String, String> data) {
            this.tokens.addAll(tokens);
        }
    }

    @BeforeEach
    void setUp() {
        sseService = new ChatSseService();
        channelMemberRepository = new FakeChannelMemberRepository();
        channelMuteRepository = new FakeChannelMuteRepository();
        deviceTokenRepository = new FakeDeviceTokenRepository();
        fcmSender = new CapturingFcmSender();
        listener = new ChatNotificationListener(
                sseService, channelMemberRepository, channelMuteRepository, deviceTokenRepository, fcmSender);

        channelRepository = new FakeChannelRepository();
        memberRepository = new FakeMemberRepository();
        teamRepository = new FakeTeamRepository();

        channel = channelRepository.save(Channel.create("General", ChannelType.GENERAL));
        Team team = teamRepository.save(Team.create("기본팀"));
        sender = saveMember(team, "보낸이", "sender@yanus.com");
        online = saveMember(team, "온라인", "online@yanus.com");
        offlineUnmuted = saveMember(team, "오프라인", "offline@yanus.com");
        offlineMuted = saveMember(team, "음소거", "muted@yanus.com");

        for (Member m : List.of(sender, online, offlineUnmuted, offlineMuted)) {
            channelMemberRepository.save(ChannelMember.create(channel, m));
        }

        // 온라인 멤버는 SSE 구독, 오프라인 멤버 2명은 디바이스 토큰 보유
        sseService.subscribe(online.getId());
        deviceTokenRepository.save(DeviceToken.create(offlineUnmuted, "token-offline"));
        deviceTokenRepository.save(DeviceToken.create(offlineMuted, "token-muted"));
        // 음소거 멤버는 채널 알림을 꺼둠
        channelMuteRepository.save(ChannelMute.create(offlineMuted, channel));
    }

    private Member saveMember(Team team, String name, String email) {
        return memberRepository.save(
                Member.create(name, email, "enc", MemberRole.MEMBER, MemberStatus.ACTIVE, team));
    }

    private NewMessageEvent newMessageEvent() {
        MessageResponse message = new MessageResponse(
                100L, channel.getId(), sender.getId(), "보낸이", "안녕", MessageType.TEXT, List.of(), LocalDateTime.now());
        return new NewMessageEvent(message, sender.getId());
    }

    @Test
    @DisplayName("오프라인+음소거 안 한 멤버에게만 FCM을 보낸다")
    void fcm_only_offline_unmuted() {
        // when
        listener.onNewMessage(newMessageEvent());

        // then: 온라인(SSE)·발신자·음소거 멤버는 제외, 오프라인 미음소거 멤버 토큰만
        assertThat(fcmSender.tokens).containsExactly("token-offline");
    }

    @Test
    @DisplayName("모든 멤버가 온라인이면 FCM은 보내지 않는다")
    void no_fcm_when_all_online() {
        // given
        sseService.subscribe(offlineUnmuted.getId());

        // when
        listener.onNewMessage(newMessageEvent());

        // then
        assertThat(fcmSender.tokens).isEmpty();
    }
}
