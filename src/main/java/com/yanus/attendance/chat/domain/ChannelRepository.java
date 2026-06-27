package com.yanus.attendance.chat.domain;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository {

    Channel save(Channel channel);

    Optional<Channel> findById(Long id);

    List<Channel> findAll();
}
