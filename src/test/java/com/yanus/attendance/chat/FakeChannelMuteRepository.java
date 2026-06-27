package com.yanus.attendance.chat;

import com.yanus.attendance.chat.domain.ChannelMute;
import com.yanus.attendance.chat.domain.ChannelMuteRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeChannelMuteRepository implements ChannelMuteRepository {

    private final List<ChannelMute> store = new ArrayList<>();
    private Long sequence = 1L;

    @Override
    public ChannelMute save(ChannelMute channelMute) {
        ReflectionTestUtils.setField(channelMute, "id", sequence++);
        store.add(channelMute);
        return channelMute;
    }

    @Override
    public boolean existsByMemberIdAndChannelId(Long memberId, Long channelId) {
        return store.stream().anyMatch(cm ->
                cm.getMember().getId().equals(memberId) && cm.getChannel().getId().equals(channelId));
    }

    @Override
    public void deleteByMemberIdAndChannelId(Long memberId, Long channelId) {
        store.removeIf(cm ->
                cm.getMember().getId().equals(memberId) && cm.getChannel().getId().equals(channelId));
    }

    @Override
    public List<Long> findChannelIdsByMemberId(Long memberId) {
        return store.stream()
                .filter(cm -> cm.getMember().getId().equals(memberId))
                .map(cm -> cm.getChannel().getId())
                .toList();
    }
}
