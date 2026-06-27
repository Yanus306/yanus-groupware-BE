package com.yanus.attendance.chat.infrastructure;

import com.yanus.attendance.chat.domain.DeviceToken;
import com.yanus.attendance.chat.domain.DeviceTokenRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class DeviceTokenJpaRepository implements DeviceTokenRepository {

    private final DeviceTokenSpringDataRepository repository;

    @Override
    public DeviceToken save(DeviceToken deviceToken) {
        return repository.save(deviceToken);
    }

    @Override
    public boolean existsByToken(String token) {
        return repository.existsByToken(token);
    }

    @Override
    @Transactional
    public void deleteByToken(String token) {
        repository.deleteByToken(token);
    }

    @Override
    public List<String> findTokensByMemberId(Long memberId) {
        return repository.findTokensByMemberId(memberId);
    }
}
