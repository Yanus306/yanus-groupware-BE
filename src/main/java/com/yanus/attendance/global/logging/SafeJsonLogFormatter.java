package com.yanus.attendance.global.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

public final class SafeJsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> FIELDS = Set.of("event", "method", "route", "status", "elapsedMs",
            "outcome", "integration", "operation", "errorCode", "job");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(password|passwd|secret|access[-_]?token|refresh[-_]?token|verification[-_]?token|authorization|cookie)(\\s*[=:]\\s*)[^\\s,;]+|Bearer\\s+[^\\s,;]+|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    @Override
    public String format(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        fields.put("environment", "prod");
        fields.put("service", "backend");
        fields.put("level", event.getLevel().toString());
        fields.put("logger", event.getLoggerName());
        fields.put("thread", event.getThreadName());
        fields.put("message", redact(event.getFormattedMessage()));
        String requestId = event.getMDCPropertyMap().get("requestId");
        if (requestId != null) {
            fields.put("requestId", requestId);
        }
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().stream().filter(pair -> FIELDS.contains(pair.key))
                    .forEach(pair -> fields.put(pair.key, pair.value instanceof Number
                            ? pair.value : redact(String.valueOf(pair.value))));
        }
        IThrowableProxy failure = event.getThrowableProxy();
        if (failure != null) {
            fields.put("exceptionType", type(failure));
            StringBuilder stack = new StringBuilder();
            appendStack(stack, failure, 0);
            fields.put("stackTrace", stack.toString());
        }
        try {
            return JSON.writeValueAsString(fields) + "\n";
        } catch (JsonProcessingException e) {
            return "{\"level\":\"ERROR\",\"event\":\"logging.serialization.failed\"}\n";
        }
    }

    private static String redact(String value) {
        return value == null ? "" : EMAIL.matcher(CREDENTIAL.matcher(value).replaceAll("[REDACTED]"))
                .replaceAll("[REDACTED]");
    }

    private static String type(IThrowableProxy failure) {
        return SafeLogThrowable.class.getName().equals(failure.getClassName())
                ? failure.getMessage() : failure.getClassName();
    }

    private static void appendStack(StringBuilder stack, IThrowableProxy failure, int depth) {
        if (failure == null || depth >= 8) {
            return;
        }
        stack.append(type(failure)).append('\n');
        var frames = failure.getStackTraceElementProxyArray();
        for (int i = 0; frames != null && i < Math.min(frames.length, 60); i++) {
            stack.append(frames[i]).append('\n');
        }
        if (failure.getSuppressed() != null) {
            for (IThrowableProxy suppressed : failure.getSuppressed()) {
                stack.append("Suppressed: ");
                appendStack(stack, suppressed, depth + 1);
            }
        }
        if (failure.getCause() != null) {
            stack.append("Caused by: ");
            appendStack(stack, failure.getCause(), depth + 1);
        }
    }
}
