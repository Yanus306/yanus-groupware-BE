package com.yanus.attendance.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yanus.attendance.auth.infrastructure.JwtAuthenticationFilter;
import com.yanus.attendance.auth.infrastructure.JwtTokenProvider;
import com.yanus.attendance.global.config.SecurityConfig;
import com.yanus.attendance.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = LoggingSecurityIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoggingSecurityIntegrationTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void anonymousAuditQueryIsDeniedAndStillReceivesRequestId() {
        var response = http.getForEntity("/api/v1/audit-logs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotBlank();
    }

    @Test
    void memberCannotReadAuditLogs() {
        assertThat(get("/api/v1/audit-logs", "member").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanReadAuditLogs() {
        assertThat(get("/api/v1/audit-logs", "admin").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void handledFailureHas500AndStableRequestId() {
        var response = get("/test/fail", "admin");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getFirst("X-Request-ID")).matches("[0-9a-f-]{36}");
        assertThat(response.getBody()).contains("INTERNAL_ERROR").doesNotContain("private-secret");
    }

    private ResponseEntity<String> get(String path, String role) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(role);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({SecurityConfig.class, RequestLoggingFilter.class, GlobalExceptionHandler.class, TestController.class})
    static class TestApplication {
        @Bean
        JwtAuthenticationFilter authenticationFilter() {
            var tokens = mock(JwtTokenProvider.class);
            for (String role : List.of("member", "admin")) {
                when(tokens.validateToken(role)).thenReturn(true);
                when(tokens.getMemberId(role)).thenReturn(1L);
                when(tokens.getRole(role)).thenReturn(role.toUpperCase(java.util.Locale.ROOT));
            }
            return new JwtAuthenticationFilter(tokens);
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/api/v1/audit-logs")
        List<String> audit() {
            return List.of();
        }

        @GetMapping("/test/fail")
        String fail() {
            throw new IllegalStateException("private-secret");
        }
    }
}
