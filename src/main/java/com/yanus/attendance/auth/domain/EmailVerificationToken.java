package com.yanus.attendance.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used;

    public static EmailVerificationToken create(String token, Long memberId, LocalDateTime expiresAt) {
        EmailVerificationToken emailVerificationToken = new EmailVerificationToken();
        emailVerificationToken.token = token;
        emailVerificationToken.memberId = memberId;
        emailVerificationToken.expiresAt = expiresAt;
        emailVerificationToken.used = false;
        return emailVerificationToken;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void use() {
        this.used = true;
    }
}
