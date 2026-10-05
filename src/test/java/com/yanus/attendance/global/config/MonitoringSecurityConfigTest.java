package com.yanus.attendance.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = MonitoringSecurityConfigTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "yanus.observability.enabled=true",
                "management.server.port=0",
                "management.server.address=127.0.0.1",
                "management.endpoints.web.exposure.include=health,prometheus",
                "management.endpoint.health.show-details=never",
                "management.health.mail.enabled=false",
                "management.metrics.distribution.percentiles-histogram.http.server.requests=true"
        })
@AutoConfigureObservability
class MonitoringSecurityConfigTest {

    @Autowired
    private TestRestTemplate http;

    @LocalManagementPort
    private int managementPort;

    @Test
    void managementPortExposesHealthAndRealPrometheusHistograms() {
        assertThat(http.getForEntity("/test", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/health", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        var response = http.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/prometheus", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("http_server_requests_seconds_bucket", "jvm_memory_used_bytes");
    }

    @Test
    void publicApplicationPortDoesNotExposeActuatorOrPrivateApi() {
        assertThat(http.getForEntity("/actuator/prometheus", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.getForEntity("/api/private", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void managementPortDoesNotExposeOtherEndpoints() {
        assertThat(http.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/env", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({MonitoringSecurityConfig.class, TestController.class})
    static class TestApplication {
        @Bean
        SecurityFilterChain applicationChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.requestMatchers("/test").permitAll()
                    .anyRequest().denyAll()).build();
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/test")
        String ok() {
            return "ok";
        }
    }
}
