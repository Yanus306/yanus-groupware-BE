package com.yanus.attendance.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.chat.FakeChannelRepository;
import com.yanus.attendance.chat.FakeMessageRepository;
import com.yanus.attendance.chat.FakeStorageService;
import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelType;
import com.yanus.attendance.chat.domain.Message;
import com.yanus.attendance.chat.domain.MessageType;
import com.yanus.attendance.chat.presentation.dto.MessageResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

public class MessageServiceTest {

    private MessageService messageService;
    private FakeMessageRepository messageRepository;
    private FakeChannelRepository channelRepository;
    private FakeMemberRepository memberRepository;
    private FakeTeamRepository teamRepository;
    private FakeStorageService storageService;

    private Channel channel;
    private Member sender;

    @BeforeEach
    void setUp() {
        messageRepository = new FakeMessageRepository();
        channelRepository = new FakeChannelRepository();
        memberRepository = new FakeMemberRepository();
        teamRepository = new FakeTeamRepository();
        storageService = new FakeStorageService();
        messageService = new MessageService(messageRepository, channelRepository, memberRepository, storageService);
        ReflectionTestUtils.setField(messageService, "bucket", "test-bucket");

        channel = channelRepository.save(Channel.create("General", ChannelType.GENERAL));
        Team team = teamRepository.save(Team.create("기본팀"));
        sender = memberRepository.save(
                Member.create("보낸이", "sender@yanus.com", "encoded", MemberRole.MEMBER, MemberStatus.ACTIVE, team));
    }

    private PageRequest latestFirst(int size) {
        return PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    @DisplayName("채널 메시지 목록을 조회한다")
    void get_messages() {
        // given
        messageRepository.save(Message.create(channel, sender, "첫 메시지", MessageType.TEXT));
        messageRepository.save(Message.create(channel, sender, "둘째 메시지", MessageType.TEXT));

        // when
        List<MessageResponse> result = messageService.getMessages(channel.getId(), latestFirst(50));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).senderName()).isEqualTo("보낸이");
        assertThat(result.get(0).channelId()).isEqualTo(channel.getId());
    }

    @Test
    @DisplayName("페이지 크기만큼만 반환한다")
    void get_messages_paged() {
        // given
        for (int i = 0; i < 5; i++) {
            messageRepository.save(Message.create(channel, sender, "메시지 " + i, MessageType.TEXT));
        }

        // when
        List<MessageResponse> result = messageService.getMessages(channel.getId(), latestFirst(2));

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("최신 메시지가 먼저 정렬된다")
    void get_messages_latest_first() {
        // given
        messageRepository.save(Message.create(channel, sender, "오래된 메시지", MessageType.TEXT));
        Message latest = messageRepository.save(Message.create(channel, sender, "최신 메시지", MessageType.TEXT));

        // when
        List<MessageResponse> result = messageService.getMessages(channel.getId(), latestFirst(50));

        // then
        assertThat(result.get(0).id()).isEqualTo(latest.getId());
    }

    @Test
    @DisplayName("존재하지 않는 채널 조회 시 예외 발생")
    void get_messages_channel_not_found() {
        // when & then
        assertThatThrownBy(() -> messageService.getMessages(999L, latestFirst(50)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHANNEL_NOT_FOUND);
    }

    @Test
    @DisplayName("텍스트 메시지를 전송한다")
    void send_text_message() {
        // when
        MessageResponse result = messageService.sendMessage(
                channel.getId(), sender.getId(), "안녕하세요", MessageType.TEXT);

        // then
        assertThat(result.content()).isEqualTo("안녕하세요");
        assertThat(result.type()).isEqualTo(MessageType.TEXT);
        assertThat(result.senderId()).isEqualTo(sender.getId());
        assertThat(messageService.getMessages(channel.getId(), latestFirst(50))).hasSize(1);
    }

    @Test
    @DisplayName("type이 없으면 TEXT로 저장된다")
    void send_message_defaults_to_text() {
        // when
        MessageResponse result = messageService.sendMessage(
                channel.getId(), sender.getId(), "타입 미지정", null);

        // then
        assertThat(result.type()).isEqualTo(MessageType.TEXT);
    }

    @Test
    @DisplayName("존재하지 않는 채널에 전송 시 예외 발생")
    void send_message_channel_not_found() {
        // when & then
        assertThatThrownBy(() -> messageService.sendMessage(999L, sender.getId(), "안녕", MessageType.TEXT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHANNEL_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 멤버가 전송 시 예외 발생")
    void send_message_member_not_found() {
        // when & then
        assertThatThrownBy(() -> messageService.sendMessage(channel.getId(), 999L, "안녕", MessageType.TEXT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("파일 첨부 메시지를 전송한다")
    void send_file_message() {
        // given
        MultipartFile file = new MockMultipartFile(
                "files", "report.pdf", "application/pdf", "dummy".getBytes());

        // when
        MessageResponse result = messageService.sendFileMessage(
                channel.getId(), sender.getId(), "파일 첨부", List.of(file));

        // then
        assertThat(result.type()).isEqualTo(MessageType.FILE);
        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).originalName()).isEqualTo("report.pdf");
        assertThat(storageService.uploadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("첨부 파일이 없으면 예외 발생")
    void send_file_message_no_file() {
        // when & then
        assertThatThrownBy(() -> messageService.sendFileMessage(channel.getId(), sender.getId(), "내용", List.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MESSAGE_FILE_REQUIRED);
    }
}
