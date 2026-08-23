package com.yanus.attendance.chat.presentation.dto;

import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelType;

public record ChannelResponse(
        Long id,
        String name,
        ChannelType type
) {
    public static ChannelResponse from(Channel channel) {
        return new ChannelResponse(channel.getId(), channel.getName(), channel.getType());
    }
}
