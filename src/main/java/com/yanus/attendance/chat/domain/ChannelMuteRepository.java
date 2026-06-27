package com.yanus.attendance.chat.domain;

import java.util.List;

public interface ChannelMuteRepository {

    ChannelMute save(ChannelMute channelMute);

    boolean existsByMemberIdAndChannelId(Long memberId, Long channelId);

    void deleteByMemberIdAndChannelId(Long memberId, Long channelId);

    List<Long> findChannelIdsByMemberId(Long memberId);
}
