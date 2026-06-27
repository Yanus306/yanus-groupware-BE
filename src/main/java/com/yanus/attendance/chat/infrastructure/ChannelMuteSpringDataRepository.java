package com.yanus.attendance.chat.infrastructure;

import com.yanus.attendance.chat.domain.ChannelMute;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMuteSpringDataRepository extends JpaRepository<ChannelMute, Long> {

    boolean existsByMemberIdAndChannelId(Long memberId, Long channelId);

    void deleteByMemberIdAndChannelId(Long memberId, Long channelId);

    @Query("SELECT cm.channel.id FROM ChannelMute cm WHERE cm.member.id = :memberId")
    List<Long> findChannelIdsByMemberId(@Param("memberId") Long memberId);
}
