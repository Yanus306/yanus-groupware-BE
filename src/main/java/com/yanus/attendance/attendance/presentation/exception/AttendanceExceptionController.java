package com.yanus.attendance.attendance.presentation.exception;

import com.yanus.attendance.attendance.application.exception.AttendanceExceptionService;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionStatus;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionType;
import com.yanus.attendance.attendance.presentation.dto.exception.AttendanceExceptionListResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "출/퇴근 예외목록 반환", description = "지각, 미출근, 미퇴근, 근무 일정 미기입 확인")
@RestController
@RequestMapping("/api/v1/attendance-exceptions")
@RequiredArgsConstructor
public class AttendanceExceptionController {

    private final AttendanceExceptionService attendanceExceptionService;

    @GetMapping
    public AttendanceExceptionListResponse getExceptions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AttendanceExceptionType type,
            @RequestParam(required = false) AttendanceExceptionStatus status,
            @RequestParam(required = false) String teamName) {
        return attendanceExceptionService.getList(date, type, status, teamName);
    }
}
