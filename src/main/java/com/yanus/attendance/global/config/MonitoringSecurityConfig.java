package com.yanus.attendance.global.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "yanus.observability.enabled", havingValue = "true")
public class MonitoringSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain monitoringFilterChain(HttpSecurity http, Environment environment)
            throws Exception {
        var endpoints = EndpointRequest.to("health", "info", "prometheus");
        return http
                .securityMatcher(request -> {
                    int configuredPort = environment.getRequiredProperty("management.server.port", Integer.class);
                    int actualPort = configuredPort == 0
                            ? environment.getProperty("local.management.port", Integer.class, -1)
                            : configuredPort;
                    return request.getLocalPort() == actualPort && endpoints.matches(request);
                })
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
