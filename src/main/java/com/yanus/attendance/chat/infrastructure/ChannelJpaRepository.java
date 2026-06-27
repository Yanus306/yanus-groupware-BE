package com.yanus.attendance.chat.infrastructure;

import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelJpaRepository implements ChannelRepository {

    private final ChannelJpaRepositoryPort port;

    public ChannelJpaRepository(ChannelJpaRepositoryPort port) {
        this.port = port;
    }

    @Override
    public Channel save(Channel channel) {
        return port.save(channel);
    }

    @Override
    public Optional<Channel> findById(Long id) {
        return port.findById(id);
    }

    @Override
    public List<Channel> findAll() {
        return port.findAll();
    }
}

interface ChannelJpaRepositoryPort extends JpaRepository<Channel, Long> {
}
