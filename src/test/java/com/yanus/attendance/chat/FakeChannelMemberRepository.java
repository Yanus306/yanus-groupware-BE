package com.yanus.attendance.chat;

import com.yanus.attendance.chat.domain.ChannelMember;
import com.yanus.attendance.chat.domain.ChannelMemberRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeChannelMemberRepository implements ChannelMemberRepository {

    private final List<ChannelMember> store = new ArrayList<>();
    private Long sequence = 1L;

    @Override
    public ChannelMember save(ChannelMember channelMember) {
        ReflectionTestUtils.setField(channelMember, "id", sequence++);
        store.add(channelMember);
        return channelMember;
    }

    @Override
    public boolean existsByChannelIdAndMemberId(Long channelId, Long memberId) {
        return store.stream().anyMatch(cm ->
                cm.getChannel().getId().equals(channelId) && cm.getMember().getId().equals(memberId));
    }

    @Override
    public List<Long> findMemberIdsByChannelId(Long channelId) {
        return store.stream()
                .filter(cm -> cm.getChannel().getId().equals(channelId))
                .map(cm -> cm.getMember().getId())
                .toList();
    }

    public int size() {
        return store.size();
    }
}
