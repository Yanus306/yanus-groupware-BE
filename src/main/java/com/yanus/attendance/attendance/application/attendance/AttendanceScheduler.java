package com.yanus.attendance.attendance.application.attendance;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceScheduler {

    private final AttendanceService attendanceService;

    @Scheduled(cron = "0 * * * * *")
    public void autoCheckOut() {
        attendanceService.autoCheckOut(LocalDate.now(), LocalTime.now());
    }
}
