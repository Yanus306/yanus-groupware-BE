package com.yanus.attendance.attendance.application.attendance;

import com.yanus.attendance.attendance.application.exception.AttendanceExceptionService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AttendanceExceptionService attendanceExceptionService;

    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Seoul")
    public void autoCheckOut() {
        attendanceExceptionService.bulkAutoCheckout(LocalDate.now(KST), null, "system");
    }
}