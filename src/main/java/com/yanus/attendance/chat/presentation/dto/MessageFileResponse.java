package com.yanus.attendance.chat.presentation.dto;

import com.yanus.attendance.chat.domain.MessageFile;

public record MessageFileResponse(
        Long id,
        String originalName,
        Long size,
        String contentType
) {
    public static MessageFileResponse from(MessageFile file) {
        return new MessageFileResponse(
                file.getId(),
                file.getOriginalName(),
                file.getSize(),
                file.getContentType()
        );
    }
}
