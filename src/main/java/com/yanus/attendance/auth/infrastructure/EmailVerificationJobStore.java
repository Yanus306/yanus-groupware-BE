package com.yanus.attendance.auth.infrastructure;

import com.yanus.attendance.auth.domain.EmailVerificationJobRepository;
import com.yanus.attendance.auth.domain.EmailVerificationTokenRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class EmailVerificationJobStore implements EmailVerificationJobRepository {

    private static final int LEASE_SECONDS = 60;

    private final JdbcTemplate jdbc;
    private final EmailVerificationTokenRepository tokens;
    private final MemberRepository members;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Long tokenId) {
        jdbc.update("INSERT INTO email_verification_job (token_id) VALUES (?)", tokenId);
    }

    @Override
    @Transactional
    public Optional<Claim> claimNext() {
        return jdbc.query("""
                WITH candidate AS (
                    SELECT id
                    FROM email_verification_job
                    WHERE (status = 'PENDING' AND next_attempt_at <= now())
                       OR (status = 'PROCESSING' AND lease_until <= now())
                    ORDER BY CASE WHEN status = 'PENDING' THEN next_attempt_at ELSE lease_until END, id
                    LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE email_verification_job j
                SET status = 'PROCESSING', attempt_count = j.attempt_count + 1,
                    lease_id = ?, lease_until = now() + (? * interval '1 second'), updated_at = now()
                FROM candidate
                WHERE j.id = candidate.id
                RETURNING j.id, j.token_id, j.lease_id, j.attempt_count, j.created_at
                """, (rs, row) -> new Claim(rs.getLong("id"), rs.getObject("token_id", Long.class),
                        rs.getObject("lease_id", UUID.class), rs.getInt("attempt_count"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                UUID.randomUUID(), LEASE_SECONDS).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Delivery> loadDelivery(Claim claim) {
        if (claim.tokenId() == null) {
            return Optional.empty();
        }
        return tokens.findById(claim.tokenId())
                .filter(token -> !token.isUsed() && !token.isExpired())
                .flatMap(token -> members.findById(token.getMemberId())
                        .filter(Member::isPending)
                        .map(member -> new Delivery(member.getEmail(), token.getToken(), token.getExpiresAt())));
    }

    @Override
    @Transactional
    public boolean markSent(Claim claim) {
        return finish(claim, "SENT", null);
    }

    @Override
    @Transactional
    public boolean cancel(Claim claim) {
        return finish(claim, "CANCELLED", "TOKEN_NOT_USABLE");
    }

    @Override
    @Transactional
    public boolean recordFailure(Claim claim, String reason, Optional<Duration> retryAfter) {
        if (retryAfter.isEmpty()) {
            return finish(claim, "FAILED", reason);
        }
        return jdbc.update("""
                UPDATE email_verification_job
                SET status = 'PENDING', next_attempt_at = now() + (? * interval '1 millisecond'),
                    lease_id = NULL, lease_until = NULL, last_error_code = ?, updated_at = now()
                WHERE id = ? AND status = 'PROCESSING' AND lease_id = ?
                """, retryAfter.get().toMillis(), reason, claim.id(), claim.leaseId()) == 1;
    }

    private boolean finish(Claim claim, String status, String reason) {
        return jdbc.update("""
                UPDATE email_verification_job
                SET status = ?, lease_id = NULL, lease_until = NULL, last_error_code = ?, updated_at = now()
                WHERE id = ? AND status = 'PROCESSING' AND lease_id = ?
                """, status, reason, claim.id(), claim.leaseId()) == 1;
    }
}
