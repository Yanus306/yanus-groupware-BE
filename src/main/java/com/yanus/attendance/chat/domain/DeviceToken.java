package com.yanus.attendance.chat.domain;

import com.yanus.attendance.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 멤버의 FCM 디바이스 토큰. 오프라인 사용자에게 푸시를 보낼 때 사용한다.
 */
@Entity
@Table(name = "device_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static DeviceToken create(Member member, String token) {
        DeviceToken deviceToken = new DeviceToken();
        deviceToken.member = member;
        deviceToken.token = token;
        deviceToken.createdAt = LocalDateTime.now();
        return deviceToken;
    }
}
