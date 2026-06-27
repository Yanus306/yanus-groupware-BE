package com.yanus.attendance.chat;

import com.yanus.attendance.chat.domain.DeviceToken;
import com.yanus.attendance.chat.domain.DeviceTokenRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeDeviceTokenRepository implements DeviceTokenRepository {

    private final List<DeviceToken> store = new ArrayList<>();
    private Long sequence = 1L;

    @Override
    public DeviceToken save(DeviceToken deviceToken) {
        ReflectionTestUtils.setField(deviceToken, "id", sequence++);
        store.add(deviceToken);
        return deviceToken;
    }

    @Override
    public boolean existsByToken(String token) {
        return store.stream().anyMatch(dt -> dt.getToken().equals(token));
    }

    @Override
    public void deleteByToken(String token) {
        store.removeIf(dt -> dt.getToken().equals(token));
    }

    @Override
    public List<String> findTokensByMemberId(Long memberId) {
        return store.stream()
                .filter(dt -> dt.getMember().getId().equals(memberId))
                .map(DeviceToken::getToken)
                .toList();
    }
}
