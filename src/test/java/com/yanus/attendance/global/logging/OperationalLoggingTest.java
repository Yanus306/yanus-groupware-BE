package com.yanus.attendance.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanus.attendance.global.exception.GlobalExceptionHandler;
import jakarta.servlet.ServletException;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class OperationalLoggingTest {
    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void formatterPreservesCauseTypesAndFramesWithoutSecrets() throws Exception {
        var failure = new IllegalStateException("password=very-secret user@example.com",
                new IllegalArgumentException("Bearer secret-jwt-value refreshToken=secret-refresh"));
        var event = new LoggingEvent(getClass().getName(), (Logger) LoggerFactory.getLogger(getClass()),
                Level.ERROR, "operation failed", failure, null);
        event.setMDCPropertyMap(Map.of("requestId", "abc123"));
        String json = new SafeJsonLogFormatter().format(event);
        assertThat(json).doesNotContain("very-secret", "user@example.com", "secret-jwt-value", "secret-refresh");
        var parsed = new ObjectMapper().readTree(json);
        assertThat(parsed.get("requestId").asText()).isEqualTo("abc123");
        assertThat(parsed.get("exceptionType").asText()).isEqualTo(IllegalStateException.class.getName());
        assertThat(parsed.get("stackTrace").asText()).contains("IllegalArgumentException", "OperationalLoggingTest");
    }

    @Test
    void requestGetsIdAndCompletionUsesRouteTemplateWithoutBodyOrQuery() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest("POST", "/api/v1/members/123");
            request.addHeader("X-Request-ID", "bad\nrequest-password");
            request.addHeader("Authorization", "Bearer header-secret");
            request.setQueryString("token=query-secret");
            request.setContent("{\"password\":\"body-secret\"}".getBytes());
            var response = new MockHttpServletResponse();
            MDC.put("parent", "preserved");
            new RequestLoggingFilter().doFilter(request, response, (req, res) -> {
                request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/members/{id}");
                assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-ID"));
                response.setStatus(201);
            });
            assertThat(response.getHeader("X-Request-ID")).matches("[0-9a-f-]{36}");
            assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("parent", "preserved"));
            assertThat(appender.list).hasSize(1);
            String json = new SafeJsonLogFormatter().format(appender.list.getFirst());
            assertThat(json).contains("/api/v1/members/{id}", "http.request.completed", "201")
                    .doesNotContain("header-secret", "query-secret", "body-secret", "request-password");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void handled500EmitsErrorWithCause() {
        var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var response = new GlobalExceptionHandler().handleException(new IllegalStateException("private-content"));
            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
            assertThat(new SafeJsonLogFormatter().format(appender.list.getFirst()))
                    .contains("IllegalStateException").doesNotContain("private-content");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void escapingExceptionDoesNotLeaveMdcBehind() {
        var request = new MockHttpServletRequest("GET", "/unmatched");
        var response = new MockHttpServletResponse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RequestLoggingFilter()
                .doFilter(request, response, (req, res) -> { throw new ServletException("secret"); }))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
