package com.yanus.attendance.chat.infrastructure;

import com.yanus.attendance.chat.domain.ChannelMute;
import com.yanus.attendance.chat.domain.ChannelMuteRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ChannelMuteJpaRepository implements ChannelMuteRepository {

    private final ChannelMuteSpringDataRepository repository;

    @Override
    public ChannelMute save(ChannelMute channelMute) {
        return repository.save(channelMute);
    }

    @Override
    public boolean existsByMemberIdAndChannelId(Long memberId, Long channelId) {
        return repository.existsByMemberIdAndChannelId(memberId, channelId);
    }

    @Override
    @Transactional
    public void deleteByMemberIdAndChannelId(Long memberId, Long channelId) {
        repository.deleteByMemberIdAndChannelId(memberId, channelId);
    }

    @Override
    public List<Long> findChannelIdsByMemberId(Long memberId) {
        return repository.findChannelIdsByMemberId(memberId);
    }
}
