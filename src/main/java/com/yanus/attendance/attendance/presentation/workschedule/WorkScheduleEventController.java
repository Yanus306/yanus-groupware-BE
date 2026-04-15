package com.yanus.attendance.attendance.presentation.workschedule;

import com.yanus.attendance.attendance.application.workschedule.WorkScheduleEventService;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleEventRequest;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleEventResponse;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "닐짜별 근무 일정", description = "특정 날짜 근무 일정 CRUD")
@RestController
@RequestMapping("/api/v1/work-schedule-events")
@RequiredArgsConstructor
public class WorkScheduleEventController {

    private final WorkScheduleEventService workScheduleEventService;

    @PostMapping
    public ResponseEntity<ApiResponse<WorkScheduleEventResponse>> createEvent(
            @AuthenticationPrincipal Long memberId,
            @RequestBody WorkScheduleEventRequest request) {
        return ResponseEntity.ok(ApiResponse.success(workScheduleEventService.createEvent(memberId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkScheduleEventResponse>>> getEvents(
            @AuthenticationPrincipal Long memberId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success(workScheduleEventService.getEvents(memberId, startDate, endDate)));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<WorkScheduleEventResponse>> updateEvent(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long eventId,
            @RequestBody WorkScheduleEventRequest request) {
        return ResponseEntity.ok(ApiResponse.success(workScheduleEventService.updateEvent(memberId, eventId, request)));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long eventId) {
        workScheduleEventService.deleteEvent(memberId, eventId);
        return ResponseEntity.noContent().build();
    }
}
