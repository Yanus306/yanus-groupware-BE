package com.yanus.attendance.chat.presentation.dto;

import com.yanus.attendance.chat.domain.Message;
import com.yanus.attendance.chat.domain.MessageType;
import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long channelId,
        Long senderId,
        String senderName,
        String content,
        MessageType type,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getChannel().getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                message.getContent(),
                message.getType(),
                message.getCreatedAt()
        );
    }
}
