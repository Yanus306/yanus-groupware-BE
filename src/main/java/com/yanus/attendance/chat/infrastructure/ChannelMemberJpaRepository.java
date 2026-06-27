package com.yanus.attendance.chat.infrastructure;

import com.yanus.attendance.chat.domain.ChannelMember;
import com.yanus.attendance.chat.domain.ChannelMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChannelMemberJpaRepository implements ChannelMemberRepository {

    private final ChannelMemberSpringDataRepository repository;

    @Override
    public ChannelMember save(ChannelMember channelMember) {
        return repository.save(channelMember);
    }

    @Override
    public boolean existsByChannelIdAndMemberId(Long channelId, Long memberId) {
        return repository.existsByChannelIdAndMemberId(channelId, memberId);
    }
}
