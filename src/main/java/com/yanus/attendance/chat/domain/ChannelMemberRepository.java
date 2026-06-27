package com.yanus.attendance.chat.domain;

import java.util.List;

public interface ChannelMemberRepository {

    ChannelMember save(ChannelMember channelMember);

    boolean existsByChannelIdAndMemberId(Long channelId, Long memberId);

    List<Long> findMemberIdsByChannelId(Long channelId);
}
