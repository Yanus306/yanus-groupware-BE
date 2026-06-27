package com.yanus.attendance.chat.infrastructure;

import com.yanus.attendance.chat.domain.Message;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageSpringDataRepository extends JpaRepository<Message, Long> {
    List<Message> findByChannelId(Long channelId, Pageable pageable);
}
