package com.yanus.attendance.chat.domain;

import java.util.List;

public interface DeviceTokenRepository {

    DeviceToken save(DeviceToken deviceToken);

    boolean existsByToken(String token);

    void deleteByToken(String token);

    List<String> findTokensByMemberId(Long memberId);
}
