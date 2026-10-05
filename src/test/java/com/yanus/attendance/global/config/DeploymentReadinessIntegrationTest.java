package com.yanus.attendance.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = DeploymentReadinessIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=optional:file:ops/observability/application-observability.properties",
                "management.server.port=0", "management.health.mail.enabled=false",
                "spring.datasource.driver-class-name=org.postgresql.Driver", "logging.file.name=build/readiness-qa.log",
                "spring.datasource.hikari.connection-timeout=1000", "spring.datasource.hikari.validation-timeout=500",
                "springdoc.api-docs.enabled=false"})
class DeploymentReadinessIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("readiness").withUsername("fixture").withPassword("fixture");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired TestRestTemplate http;
    @LocalManagementPort int managementPort;

    @Test
    void privateReadinessTracksActualDatabaseWhileLivenessAndPublicSecurityRemainIndependent() {
        String management = "http://127.0.0.1:" + managementPort + "/actuator/";
        var healthy = http.getForEntity(management + "health/readiness", String.class);
        assertThat(healthy.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(healthy.getBody()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(http.getForEntity(management + "info", String.class).getBody())
                .contains("\"commit\":\"candidate-test\"").doesNotContain("password", "fixture");
        assertThat(http.getForEntity("/v3/api-docs", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        for (String path : new String[]{"info", "health", "health/readiness", "env"}) {
            assertThat(http.getForEntity("/actuator/" + path, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        DATABASE.stop();
        var unavailable = http.getForEntity(management + "health/readiness", String.class);
        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unavailable.getBody()).isEqualTo("{\"status\":\"DOWN\"}");
        assertThat(http.getForEntity(management + "health/liveness", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({MonitoringSecurityConfig.class, TestController.class})
    static class TestApplication {
        @Bean BuildProperties buildProperties() {
            var properties = new Properties();
            properties.put("commit", "candidate-test");
            return new BuildProperties(properties);
        }
        @Bean SecurityFilterChain applicationChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.requestMatchers("/v3/api-docs").permitAll()
                    .anyRequest().denyAll()).build();
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/v3/api-docs")
        Map<String, Object> api() {
            return Map.of("openapi", "3.1.0", "info", Map.of("title", "yANUs groupware API"));
        }
    }
}
