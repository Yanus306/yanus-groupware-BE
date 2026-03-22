package com.yanus.attendance.calendar.presentation;

import com.yanus.attendance.calendar.application.CalendarEventService;
import com.yanus.attendance.calendar.presentation.dto.CalendarEventCreateRequest;
import com.yanus.attendance.calendar.presentation.dto.CalendarEventResponse;
import com.yanus.attendance.global.response.ApiResponse;
import java.util.List;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    @PostMapping
    public ResponseEntity<ApiResponse<CalendarEventResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @RequestBody CalendarEventCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(calendarEventService.create(memberId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success(calendarEventService.getByDateRange(startDate, endDate)));
    }

    @GetMapping("/me")
    public  ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getByCreatedBy(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(calendarEventService.getByCreatedBy(memberId)));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> update(
            @PathVariable Long eventId,
            @RequestBody CalendarEventCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(calendarEventService.update(eventId, request)));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long eventId) {
        calendarEventService.delete(eventId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
