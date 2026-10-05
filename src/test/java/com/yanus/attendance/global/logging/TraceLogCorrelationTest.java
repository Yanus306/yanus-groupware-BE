package com.yanus.attendance.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class TraceLogCorrelationTest {
    private String format(Map<String, String> context) {
        var event = new LoggingEvent(getClass().getName(), (Logger) LoggerFactory.getLogger(getClass()),
                Level.INFO, "Request completed", null, null);
        event.setMDCPropertyMap(context);
        return new SafeJsonLogFormatter().format(event);
    }

    @Test
    void preservesAdditionalTraceFlagBitsAndRejectsMalformedFlags() throws Exception {
        for (String flags : new String[] { "00", "01", "02", "03" }) {
            var json = new ObjectMapper().readTree(format(Map.of("trace_id", "a".repeat(32),
                    "span_id", "b".repeat(16), "trace_flags", flags)));
            assertThat(json.get("traceFlags").asText()).isEqualTo(flags);
        }
        assertThat(format(Map.of("trace_id", "a".repeat(32), "span_id", "b".repeat(16),
                "trace_flags", "secret\n"))).doesNotContain("traceFlags", "secret");
    }

    @Test
    void agentContextCorrelatesLogsAndPreservesExistingRequestId() throws Exception {
        var json = new ObjectMapper().readTree(format(Map.of("requestId", "existing-request-id",
                "trace_id", "20320320320320320320320320320320", "span_id", "1234567890abcdef", "trace_flags", "01")));
        assertThat(json.get("requestId").asText()).isEqualTo("existing-request-id");
        assertThat(json.get("traceId").asText()).isEqualTo("20320320320320320320320320320320");
        assertThat(json.get("spanId").asText()).isEqualTo("1234567890abcdef");
        assertThat(json.get("traceFlags").asText()).isEqualTo("01");
    }

    @Test
    void malformedOrZeroContextIsNotWrittenToLogs() {
        for (String trace : new String[] { "malicious\nsecret", "0".repeat(32), "a".repeat(33) }) {
            assertThat(format(Map.of("trace_id", trace, "span_id", "1234567890abcdef")))
                    .doesNotContain("traceId", "spanId", "malicious", "secret");
        }
        assertThat(format(Map.of("trace_id", "a".repeat(32), "span_id", "0".repeat(16))))
                .doesNotContain("traceId", "spanId");
        assertThat(format(Map.of())).doesNotContain("traceId", "spanId");
    }
}
