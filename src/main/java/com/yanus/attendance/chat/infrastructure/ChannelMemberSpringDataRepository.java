package com.yanus.attendance.chat.infrastructure;

import com.yanus.attendance.chat.domain.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMemberSpringDataRepository extends JpaRepository<ChannelMember, Long> {
    boolean existsByChannelIdAndMemberId(Long channelId, Long memberId);
}
