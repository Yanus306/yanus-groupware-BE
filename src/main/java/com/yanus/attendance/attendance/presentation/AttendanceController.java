package com.yanus.attendance.attendance.presentation;

import com.yanus.attendance.attendance.application.AttendanceService;
import com.yanus.attendance.attendance.presentation.dto.AttendanceResponse;
import com.yanus.attendance.global.response.ApiResponse;
import com.yanus.attendance.team.domain.TeamName;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "출근", description = "출근, 퇴근, 상태 확인, 팀 출근 확인")
@RestController
@RequestMapping("/api/v1/attendances")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkIn(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.checkIn(memberId)));
    }

    @PostMapping("/check-out")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkOut(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.checkOut(memberId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getMyAttendances(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getMyAttendances(memberId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getAttendancesByFilter(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) TeamName teamName
    ) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getAttendancesByFilter(date, teamName)));
    }
}
