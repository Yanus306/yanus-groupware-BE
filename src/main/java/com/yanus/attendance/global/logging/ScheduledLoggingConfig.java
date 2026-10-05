package com.yanus.attendance.global.logging;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ScheduledLoggingConfig {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledLoggingConfig.class);

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("yanus-scheduled-");
        scheduler.setErrorHandler(failure -> {
            MDC.put("jobOutcome", "failed");
            LOG.atError().addKeyValue("event", "scheduled.failed")
                    .setCause(SafeLogThrowable.from(failure)).log("Scheduled operation failed");
        });
        scheduler.setTaskDecorator(task -> () -> {
            var previous = MDC.getCopyOfContextMap();
            MDC.put("requestId", UUID.randomUUID().toString());
            MDC.put("jobOutcome", "success");
            long started = System.nanoTime();
            LOG.atInfo().addKeyValue("event", "scheduled.started").log("Scheduled operation started");
            try {
                task.run();
            } finally {
                try {
                    LOG.atInfo().addKeyValue("event", "scheduled.completed")
                            .addKeyValue("outcome", MDC.get("jobOutcome"))
                            .addKeyValue("elapsedMs", (System.nanoTime() - started) / 1_000_000.0)
                            .log("Scheduled operation completed");
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            }
        });
        return scheduler;
    }
}
