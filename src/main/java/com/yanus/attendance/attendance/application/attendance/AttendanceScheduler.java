package com.yanus.attendance.attendance.application.attendance;

import com.yanus.attendance.attendance.application.exception.AttendanceExceptionService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceScheduler {

    private final AttendanceExceptionService attendanceExceptionService;

    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Seoul")
    public void autoCheckOut() {
        attendanceExceptionService.bulkAutoCheckout(LocalDate.now(), null, "system");
    }
}