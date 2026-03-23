package com.yanus.attendance.attendance.application;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceScheduler {

    private final AttendanceService attendanceService;

    @Scheduled(cron = "0 0 0 * * *")
    public void autoCheckOut() {
        LocalDate yesterday =LocalDate.now().minusDays(1);
        attendanceService.autoCheckOut(yesterday);
    }
}
