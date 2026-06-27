package com.yanus.attendance.chat.infrastructure;

import com.yanus.attendance.chat.domain.Message;
import com.yanus.attendance.chat.domain.MessageRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MessageJpaRepository implements MessageRepository {

    private final MessageSpringDataRepository repository;

    @Override
    public Message save(Message message) {
        return repository.save(message);
    }

    @Override
    public Optional<Message> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<Message> findByChannelId(Long channelId, Pageable pageable) {
        return repository.findByChannelId(channelId, pageable);
    }
}
