package com.yanus.attendance.chat;

import com.yanus.attendance.chat.domain.Message;
import com.yanus.attendance.chat.domain.MessageRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeMessageRepository implements MessageRepository {

    private final Map<Long, Message> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public Message save(Message message) {
        if (message.getId() == null) {
            ReflectionTestUtils.setField(message, "id", sequence++);
        }
        store.put(message.getId(), message);
        return message;
    }

    @Override
    public Optional<Message> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Message> findByChannelId(Long channelId, Pageable pageable) {
        Comparator<Message> byCreatedAt = Comparator.comparing(Message::getCreatedAt)
                .thenComparing(Message::getId);
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        if (order == null || order.isDescending()) {
            byCreatedAt = byCreatedAt.reversed();
        }
        return store.values().stream()
                .filter(m -> m.getChannel().getId().equals(channelId))
                .sorted(byCreatedAt)
                .skip(pageable.isPaged() ? pageable.getOffset() : 0)
                .limit(pageable.isPaged() ? pageable.getPageSize() : Long.MAX_VALUE)
                .toList();
    }
}
