package com.yanus.attendance.chat.domain;

public interface ChannelMemberRepository {

    ChannelMember save(ChannelMember channelMember);

    boolean existsByChannelIdAndMemberId(Long channelId, Long memberId);
}
