package com.yanus.attendance.attendance.presentation;

import com.yanus.attendance.attendance.application.WorkScheduleService;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleRequest;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleResponse;
import com.yanus.attendance.global.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-schedules")
@RequiredArgsConstructor
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;

    @PutMapping
    public ResponseEntity<ApiResponse<WorkScheduleResponse>> setWorkSchedule(
            @AuthenticationPrincipal Long memberId,
            @RequestBody WorkScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(workScheduleService.setWorkSchedule(memberId, request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<WorkScheduleResponse>>> getMyWorkSchedule(
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(ApiResponse.success(workScheduleService.getMyWorkSchedules(memberId)));
    }
}
