package com.yanus.attendance.chat.application;

import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelRepository;
import com.yanus.attendance.chat.domain.Message;
import com.yanus.attendance.chat.domain.MessageRepository;
import com.yanus.attendance.chat.domain.MessageType;
import com.yanus.attendance.chat.application.event.NewMessageEvent;
import com.yanus.attendance.chat.presentation.dto.MessageResponse;
import com.yanus.attendance.drive.domain.StorageService;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final MemberRepository memberRepository;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${minio.bucket}")
    private String bucket;

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
        MessageResponse response = MessageResponse.from(messageRepository.save(message));
        eventPublisher.publishEvent(new NewMessageEvent(response, senderId));
        return response;
    }

    @Transactional
    public MessageResponse sendFileMessage(Long channelId, Long senderId, String content, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.MESSAGE_FILE_REQUIRED);
        }
        Channel channel = findChannel(channelId);
        Member sender = findMember(senderId);
        Message message = Message.create(channel, sender, content, MessageType.FILE);
        for (MultipartFile file : files) {
            String storedName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            storageService.upload(file, storedName);
            message.addFile(file.getOriginalFilename(), storedName, bucket, file.getSize(), file.getContentType());
        }
        MessageResponse response = MessageResponse.from(messageRepository.save(message));
        eventPublisher.publishEvent(new NewMessageEvent(response, senderId));
        return response;
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
