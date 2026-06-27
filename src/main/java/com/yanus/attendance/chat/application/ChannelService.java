package com.yanus.attendance.chat.application;

import com.yanus.attendance.chat.domain.ChannelRepository;
import com.yanus.attendance.chat.presentation.dto.ChannelResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelService {

    private final ChannelRepository channelRepository;

    public List<ChannelResponse> findAll() {
        return channelRepository.findAll().stream()
                .map(ChannelResponse::from)
                .toList();
    }
}
