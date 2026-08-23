package com.yanus.attendance.calendar.presentation;

import com.yanus.attendance.calendar.application.CalendarEventService;
import com.yanus.attendance.calendar.application.dto.CalendarEventCreateCommand;
import com.yanus.attendance.calendar.presentation.dto.CalendarEventCreateRequest;
import com.yanus.attendance.calendar.presentation.dto.CalendarEventResponse;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "일정", description = "일정 생성, 조회, 삭제, 수정, 확인")
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    @PostMapping
    public ResponseEntity<ApiResponse<CalendarEventResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @RequestBody CalendarEventCreateRequest request) {
        CalendarEventCreateCommand command = new CalendarEventCreateCommand(
                request.title(),
                request.startDate(),
                request.startTime(),
                request.endDate(),
                request.endTime()
        );
        com.yanus.attendance.calendar.application.dto.CalendarEventResponse response =
                calendarEventService.create(memberId, command);
        return ResponseEntity.ok(ApiResponse.success(CalendarEventResponse.from(response)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<CalendarEventResponse> responses = calendarEventService.getByDateRange(startDate, endDate).stream()
                .map(CalendarEventResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/me")
    public  ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getByCreatedBy(
            @AuthenticationPrincipal Long memberId) {
        List<CalendarEventResponse> responses = calendarEventService.getByCreatedBy(memberId).stream()
                .map(CalendarEventResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> update(
            @PathVariable Long eventId,
            @RequestBody CalendarEventCreateRequest request) {
        CalendarEventCreateCommand command = new CalendarEventCreateCommand(
                request.title(),
                request.startDate(),
                request.startTime(),
                request.endDate(),
                request.endTime()
        );
        com.yanus.attendance.calendar.application.dto.CalendarEventResponse response =
                calendarEventService.update(eventId, command);
        return ResponseEntity.ok(ApiResponse.success(CalendarEventResponse.from(response)));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long eventId) {
        calendarEventService.delete(eventId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
