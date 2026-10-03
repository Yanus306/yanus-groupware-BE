package com.yanus.attendance.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern ID = Pattern.compile("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var previous = MDC.getCopyOfContextMap();
        String supplied = request.getHeader("X-Request-ID");
        String id = supplied != null && ID.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
        MDC.put("requestId", id);
        response.setHeader("X-Request-ID", id);
        long started = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            failed = true;
            LOG.atError().addKeyValue("event", "http.request.failed")
                    .setCause(SafeLogThrowable.from(e)).log("Unhandled request failure");
            throw e;
        } finally {
            try {
                Object template = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String route = template == null ? "/unmatched" : template.toString();
                String event = route.startsWith("/api/v1/auth/") ? "auth.request.completed" : "http.request.completed";
                LOG.atInfo().addKeyValue("event", event).addKeyValue("method", request.getMethod())
                        .addKeyValue("route", route).addKeyValue("status", failed ? 500 : response.getStatus())
                        .addKeyValue("elapsedMs", (System.nanoTime() - started) / 1_000_000.0)
                        .log("Request completed");
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        }
    }
}
