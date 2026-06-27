package com.yanus.attendance.chat.infrastructure;

import com.yanus.attendance.chat.domain.DeviceToken;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenSpringDataRepository extends JpaRepository<DeviceToken, Long> {

    boolean existsByToken(String token);

    void deleteByToken(String token);

    @Query("SELECT dt.token FROM DeviceToken dt WHERE dt.member.id = :memberId")
    List<String> findTokensByMemberId(@Param("memberId") Long memberId);
}
