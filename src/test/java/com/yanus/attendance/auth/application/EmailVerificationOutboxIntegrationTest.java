package com.yanus.attendance.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.yanus.attendance.AttendanceApplication;
import com.yanus.attendance.auth.application.dto.RegisterCommand;
import com.yanus.attendance.auth.domain.EmailVerificationJobRepository;
import com.yanus.attendance.auth.domain.EmailVerificationJobRepository.Claim;
import com.yanus.attendance.global.exception.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = AttendanceApplication.class, properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "yanus.email-verification.worker-enabled=false"
})
class EmailVerificationOutboxIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16.15");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired EmailVerificationService verification;
    @Autowired EmailVerificationJobRepository jobs;
    @Autowired PlatformTransactionManager manager;
    private long teamId;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE email_verification_job, email_verification_token, member, team RESTART IDENTITY CASCADE");
        teamId = jdbc.queryForObject("INSERT INTO team(name, created_at) VALUES ('test', now()) RETURNING team_id",
                Long.class);
    }

    @Test
    void registerCommitsMemberTokenAndJobTogether() {
        var tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            register();
            assertThat(count("email_verification_job")).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(count("member")).isZero();
        assertThat(count("email_verification_token")).isZero();
        assertThat(count("email_verification_job")).isZero();

        register();

        assertThat(count("member")).isEqualTo(1);
        assertThat(count("email_verification_token")).isEqualTo(1);
        assertThat(jobStatuses()).containsExactly("PENDING");
    }

    @Test
    void jobInsertFailureRollsBackMemberAndToken() {
        jdbc.execute("ALTER TABLE email_verification_job ADD CONSTRAINT fixture_failure CHECK (false) NOT VALID");
        try {
            assertThatThrownBy(this::register).isInstanceOf(RuntimeException.class);
            assertThat(count("member")).isZero();
            assertThat(count("email_verification_token")).isZero();
            assertThat(count("email_verification_job")).isZero();
        } finally {
            jdbc.execute("ALTER TABLE email_verification_job DROP CONSTRAINT fixture_failure");
        }
    }

    @Test
    void workersClaimOnceAndAnExpiredOwnerCannotRecordAnOutcome() throws Exception {
        register();
        List<Optional<Claim>> claims = concurrent(jobs::claimNext, jobs::claimNext);
        assertThat(claims).filteredOn(Optional::isPresent).hasSize(1);
        Claim original = claims.stream().flatMap(Optional::stream).findFirst().orElseThrow();

        jdbc.update("UPDATE email_verification_job SET lease_until=now()-interval '1 second'");
        Claim recovered = jobs.claimNext().orElseThrow();
        assertThat(recovered.id()).isEqualTo(original.id());
        assertThat(recovered.leaseId()).isNotEqualTo(original.leaseId());
        assertThat(recovered.attempt()).isEqualTo(2);

        assertThat(jobs.markSent(original)).isFalse();
        assertThat(jobs.markSent(recovered)).isTrue();
        assertThat(jobStatuses()).containsExactly("SENT");
    }

    @Test
    void concurrentResendsReplaceOldTokensAndAWorkerOnlySendsTheCurrentOne() throws Exception {
        register();
        String original = currentToken();
        concurrent(() -> { verification.resend("user@local.test"); return true; },
                () -> { verification.resend("user@local.test"); return true; });

        assertThat(count("email_verification_token")).isEqualTo(1);
        assertThatThrownBy(() -> verification.verify(original)).isInstanceOf(BusinessException.class);
        var mail = mock(EmailService.class);
        var worker = worker(mail);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(call.<String>getArgument(1)).isEqualTo(currentToken());
            return null;
        }).when(mail).sendVerificationEmail(anyString(), anyString());

        worker.poll();
        worker.poll();
        worker.poll();

        assertThat(jobStatuses()).containsExactly("CANCELLED", "CANCELLED", "SENT");
        verification.verify(currentToken());
        assertThat(jdbc.queryForObject("SELECT status FROM member", String.class)).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> verification.verify(currentToken())).isInstanceOf(BusinessException.class);
        verification.resend("user@local.test");
        assertThat(count("email_verification_job")).isEqualTo(3);
    }

    @Test
    void verifyAndResendShareTheMemberLock() throws Exception {
        register();
        String original = currentToken();
        List<Boolean> outcomes = concurrent(() -> {
            try {
                verification.verify(original);
                return true;
            } catch (BusinessException invalidatedByResend) {
                return false;
            }
        }, () -> { verification.resend("user@local.test"); return true; });

        assertThat(count("email_verification_token")).isEqualTo(1);
        if (outcomes.getFirst()) {
            assertThat(jobStatuses()).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT status FROM member", String.class)).isEqualTo("ACTIVE");
        } else {
            assertThat(jobStatuses()).hasSize(2);
            assertThat(jdbc.queryForObject("SELECT status FROM member", String.class)).isEqualTo("PENDING");
            verification.verify(currentToken());
        }
        assertThatThrownBy(() -> verification.verify(original)).isInstanceOf(BusinessException.class);
    }

    @Test
    void temporarySmtpFailureRetriesAndPermanentFailureStops() {
        register();
        var mail = mock(EmailService.class);
        var worker = worker(mail);
        doThrow(smtpError(451)).doThrow(smtpError(550))
                .when(mail).sendVerificationEmail(anyString(), anyString());

        worker.poll();
        assertThat(jobStatuses()).containsExactly("PENDING");
        assertThat(jobs.claimNext()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM email_verification_job", String.class))
                .isEqualTo("SMTP_451");

        jdbc.update("UPDATE email_verification_job SET next_attempt_at=now()-interval '1 second'");
        worker.poll();
        assertThat(jobStatuses()).containsExactly("FAILED");
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM email_verification_job", String.class))
                .isEqualTo("SMTP_550");
    }

    @Test
    void expiredTokenAndExhaustedLeaseAreTerminal() {
        register();
        jdbc.update("UPDATE email_verification_token SET expires_at=TIMESTAMP '2000-01-01'");
        var mail = mock(EmailService.class);
        var worker = worker(mail);
        worker.poll();
        assertThat(jobStatuses()).containsExactly("CANCELLED");

        verification.resend("user@local.test");
        jobs.claimNext().orElseThrow();
        jdbc.update("""
                UPDATE email_verification_job SET attempt_count=4, lease_until=now()-interval '1 second'
                WHERE status='PROCESSING'
                """);
        worker.poll();
        assertThat(jobStatuses()).containsExactly("CANCELLED", "FAILED");
    }

    private void register() {
        auth.register(new RegisterCommand("User", "user@local.test", "test-password", teamId));
    }

    private EmailVerificationWorker worker(EmailService mail) {
        return new EmailVerificationWorker(jobs, mail, new SimpleMeterRegistry());
    }

    private String currentToken() {
        return jdbc.queryForObject("SELECT token FROM email_verification_token", String.class);
    }

    private List<String> jobStatuses() {
        return jdbc.queryForList("SELECT status FROM email_verification_job ORDER BY id", String.class);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private MailSendException smtpError(int code) {
        return new MailSendException("Fixture SMTP failure", null,
                java.util.Map.of("fixture", new SMTPSendFailedException(".", code,
                        "Fixture", null, null, null, null)));
    }

    private <T> List<T> concurrent(Callable<T> first, Callable<T> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> startTogether(first, ready, start));
            var b = executor.submit(() -> startTogether(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private <T> T startTogether(Callable<T> call, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return call.call();
    }
}
