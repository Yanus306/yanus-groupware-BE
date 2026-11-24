package com.yanus.attendance.auth.application;

import com.yanus.attendance.auth.domain.EmailVerificationJobRepository;
import com.yanus.attendance.auth.domain.EmailVerificationJobRepository.Claim;
import com.yanus.attendance.auth.domain.EmailVerificationJobRepository.Delivery;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "yanus.email-verification.worker-enabled", havingValue = "true", matchIfMissing = true)
public class EmailVerificationWorker {

    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationWorker.class);
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(2));

    private final EmailVerificationJobRepository jobs;
    private final EmailService emailService;
    private final MeterRegistry metrics;

    @Scheduled(scheduler = "verificationMailScheduler", fixedDelayString = "${yanus.email-verification.poll-delay:1000}")
    public void poll() {
        jobs.claimNext().ifPresent(this::deliver);
    }

    private void deliver(Claim claim) {
        var delivery = jobs.loadDelivery(claim);
        if (delivery.isEmpty()) {
            if (jobs.cancel(claim)) {
                count("cancelled");
            }
            return;
        }
        if (claim.attempt() > RETRY_DELAYS.size() + 1) {
            recordFailure(claim, "LEASE_EXPIRED", Optional.empty());
            return;
        }

        try {
            emailService.sendVerificationEmail(delivery.get().email(), delivery.get().token());
        } catch (MailException failure) {
            recordMailFailure(claim, delivery.get(), failure);
            return;
        }

        if (jobs.markSent(claim)) {
            count("sent");
            metrics.timer("yanus.email.verification.delivery")
                    .record(Duration.between(claim.createdAt(), Instant.now()));
        }
    }

    private void recordMailFailure(Claim claim, Delivery delivery, MailException failure) {
        int code = smtpCode(failure);
        boolean permanent = code >= 500 || failure instanceof MailAuthenticationException
                || failure instanceof MailPreparationException;
        Optional<Duration> delay = Optional.empty();
        if (!permanent && claim.attempt() <= RETRY_DELAYS.size()) {
            Duration next = RETRY_DELAYS.get(claim.attempt() - 1);
            if (delivery.expiresAt().isAfter(LocalDateTime.now().plus(next))) {
                delay = Optional.of(next);
            }
        }
        recordFailure(claim, code == 0 ? "SMTP_ERROR" : "SMTP_" + code, delay);
    }

    private void recordFailure(Claim claim, String reason, Optional<Duration> delay) {
        if (jobs.recordFailure(claim, reason, delay)) {
            String outcome = delay.isPresent() ? "retry" : "failed";
            count(outcome);
            LOG.atWarn().addKeyValue("event", "email-verification.job").addKeyValue("job", claim.id())
                    .addKeyValue("outcome", outcome).addKeyValue("errorCode", reason)
                    .log("Email verification delivery failed");
        }
    }

    private void count(String outcome) {
        metrics.counter("yanus.email.verification.jobs", "outcome", outcome).increment();
    }

    private int smtpCode(MailException failure) {
        if (failure instanceof MailSendException sendFailure) {
            for (Exception exception : sendFailure.getMessageExceptions()) {
                for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                    int code = switch (cause) {
                        case SMTPSendFailedException smtp -> smtp.getReturnCode();
                        case SMTPAddressFailedException smtp -> smtp.getReturnCode();
                        case SMTPSenderFailedException smtp -> smtp.getReturnCode();
                        default -> 0;
                    };
                    if (code != 0) {
                        return code;
                    }
                }
            }
        }
        return 0;
    }
}
