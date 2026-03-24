package com.yanus.attendance.attendance.presentation;

import com.yanus.attendance.attendance.application.WorkScheduleService;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleRequest;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleResponse;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.DayOfWeek;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "근무 일정", description = "근무 일정 등록, 확인")
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

    @DeleteMapping("/{dayOfWeek}")
    public ResponseEntity<ApiResponse<Void>> deleteWorkSchedule(
            @AuthenticationPrincipal Long memberId,
            @PathVariable DayOfWeek dayOfWeek) {
        workScheduleService.deleteWorkSchedule(memberId, dayOfWeek);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
