package com.yanus.attendance.chat.application;

import com.yanus.attendance.chat.domain.ChannelRepository;
import com.yanus.attendance.chat.domain.MessageRepository;
import com.yanus.attendance.chat.presentation.dto.MessageResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
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

    public List<MessageResponse> getMessages(Long channelId, Pageable pageable) {
        validateChannelExists(channelId);
        return messageRepository.findByChannelId(channelId, pageable).stream()
                .map(MessageResponse::from)
                .toList();
    }

    private void validateChannelExists(Long channelId) {
        if (channelRepository.findById(channelId).isEmpty()) {
            throw new BusinessException(ErrorCode.CHANNEL_NOT_FOUND);
        }
    }
}
