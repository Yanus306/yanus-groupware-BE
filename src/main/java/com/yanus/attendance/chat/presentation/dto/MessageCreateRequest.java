package com.yanus.attendance.chat.presentation.dto;

import com.yanus.attendance.chat.domain.MessageType;

public record MessageCreateRequest(
        String content,
        MessageType type
) {
}
