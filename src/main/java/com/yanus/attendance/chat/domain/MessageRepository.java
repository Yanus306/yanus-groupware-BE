package com.yanus.attendance.chat.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface MessageRepository {

    Message save(Message message);

    Optional<Message> findById(Long id);

    List<Message> findByChannelId(Long channelId, Pageable pageable);
}
