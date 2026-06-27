package com.yanus.attendance.chat.application;

import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelRepository;
import com.yanus.attendance.chat.domain.Message;
import com.yanus.attendance.chat.domain.MessageRepository;
import com.yanus.attendance.chat.domain.MessageType;
import com.yanus.attendance.chat.presentation.dto.MessageResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final MemberRepository memberRepository;

    public List<MessageResponse> getMessages(Long channelId, Pageable pageable) {
        findChannel(channelId);
        return messageRepository.findByChannelId(channelId, pageable).stream()
                .map(MessageResponse::from)
                .toList();
    }

    @Transactional
    public MessageResponse sendMessage(Long channelId, Long senderId, String content, MessageType type) {
        Channel channel = findChannel(channelId);
        Member sender = findMember(senderId);
        MessageType resolvedType = type == null ? MessageType.TEXT : type;
        Message message = Message.create(channel, sender, content, resolvedType);
        return MessageResponse.from(messageRepository.save(message));
    }

    private Channel findChannel(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
