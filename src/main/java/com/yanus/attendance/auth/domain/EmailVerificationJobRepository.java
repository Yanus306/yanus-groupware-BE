package com.yanus.attendance.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationJobRepository {

    void enqueue(Long tokenId);

    Optional<Claim> claimNext();

    Optional<Delivery> loadDelivery(Claim claim);

    boolean markSent(Claim claim);

    boolean cancel(Claim claim);

    boolean recordFailure(Claim claim, String reason, Optional<Duration> retryAfter);

    record Claim(long id, Long tokenId, UUID leaseId, int attempt, Instant createdAt) {}

    record Delivery(String email, String token, LocalDateTime expiresAt) {
        @Override
        public String toString() {
            return "Delivery[expiresAt=" + expiresAt + "]";
        }
    }
}
