package com.yanus.attendance.attendance.infrastructure.attendance;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AttendanceClockConfiguration {
    @Bean
    Clock attendanceClock() {
        return Clock.systemUTC();
    }
}
