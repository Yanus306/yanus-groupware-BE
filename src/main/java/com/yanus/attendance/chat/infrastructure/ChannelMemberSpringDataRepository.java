package com.yanus.attendance.chat.infrastructure;

import com.yanus.attendance.chat.domain.ChannelMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelMemberSpringDataRepository extends JpaRepository<ChannelMember, Long> {
    boolean existsByChannelIdAndMemberId(Long channelId, Long memberId);

    @Query("SELECT cm.member.id FROM ChannelMember cm WHERE cm.channel.id = :channelId")
    List<Long> findMemberIdsByChannelId(@Param("channelId") Long channelId);
}
