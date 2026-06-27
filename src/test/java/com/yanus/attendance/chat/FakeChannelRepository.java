package com.yanus.attendance.chat;

import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeChannelRepository implements ChannelRepository {

    private final Map<Long, Channel> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public Channel save(Channel channel) {
        ReflectionTestUtils.setField(channel, "id", sequence++);
        store.put(channel.getId(), channel);
        return channel;
    }

    @Override
    public Optional<Channel> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Channel> findAll() {
        return new ArrayList<>(store.values());
    }
}
