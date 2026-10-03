package com.yanus.attendance.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ScheduledLoggingTest {
    @Test
    void failedTaskHasOneErrorAndOneIdAndNextTaskStillRuns() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(ScheduledLoggingConfig.class);
        List<String> events = new CopyOnWriteArrayList<>();
        var complete = new CountDownLatch(2);
        var formatter = new SafeJsonLogFormatter();
        var appender = new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent event) {
                String line = formatter.format(event);
                events.add(line);
                if (line.contains("scheduled.completed")) {
                    complete.countDown();
                }
            }
        };
        appender.start();
        logger.addAppender(appender);
        var scheduler = new ScheduledLoggingConfig().taskScheduler();
        scheduler.initialize();
        try {
            scheduler.schedule(() -> { throw new IllegalStateException("password=secret-scheduled"); }, Instant.now());
            scheduler.schedule(() -> {}, Instant.now().plusMillis(50));
            assertThat(complete.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(events.stream().filter(line -> line.contains("scheduled.failed"))).hasSize(1);
            assertThat(String.join("", events)).doesNotContain("secret-scheduled");
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            String firstId = json.readTree(events.getFirst()).get("requestId").asText();
            assertThat(events.subList(0, 3)).allMatch(line -> line.contains(firstId));
            assertThat(json.readTree(events.get(2)).get("outcome").asText()).isEqualTo("failed");
            assertThat(json.readTree(events.getLast()).get("requestId").asText()).isNotEqualTo(firstId);
            assertThat(json.readTree(events.getLast()).get("outcome").asText()).isEqualTo("success");
        } finally {
            scheduler.shutdown();
            logger.detachAppender(appender);
        }
    }
}
