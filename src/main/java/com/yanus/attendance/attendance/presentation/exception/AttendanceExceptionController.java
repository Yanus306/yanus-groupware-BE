package com.yanus.attendance.attendance.presentation.exception;

import com.yanus.attendance.attendance.application.exception.AttendanceExceptionService;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionStatus;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionType;
import com.yanus.attendance.attendance.presentation.dto.exception.AttendanceExceptionListResponse;
import com.yanus.attendance.attendance.presentation.dto.exception.AttendanceExceptionNoteRequest;
import com.yanus.attendance.attendance.presentation.dto.exception.AttendanceExceptionResponse;
import com.yanus.attendance.attendance.presentation.dto.exception.AttendanceExceptionUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "출/퇴근 예외 처리", description = "예외 조회 / 승인 / 반려 / 해결 / 메모 수정")
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

    @PatchMapping("/{id}")
    public AttendanceExceptionResponse update(
            @PathVariable Long id,
            @RequestBody AttendanceExceptionUpdateRequest request) {
        return attendanceExceptionService.updateNote(id, request.note(), request.reason());
    }

    @PostMapping("/{id}/approve")
    public AttendanceExceptionResponse approve(
            @PathVariable Long id,
            @AuthenticationPrincipal Long memberId,
            @RequestBody(required = false) AttendanceExceptionNoteRequest request) {
        return attendanceExceptionService.approve(id, resolveActor(memberId), noteOf(request));
    }

    @PostMapping("/{id}/reject")
    public AttendanceExceptionResponse reject(
            @PathVariable Long id,
            @RequestBody(required = false) AttendanceExceptionNoteRequest request) {
        return attendanceExceptionService.reject(id, noteOf(request));
    }

    @PostMapping("/{id}/resolve")
    public AttendanceExceptionResponse resolve(
            @PathVariable Long id,
            @AuthenticationPrincipal Long memberId,
            @RequestBody(required = false) AttendanceExceptionNoteRequest request) {
        return attendanceExceptionService.resolve(id, resolveActor(memberId), noteOf(request));
    }

    private String resolveActor(Long memberId) {
        if (memberId == null) {
            return "system";
        }
        return String.valueOf(memberId);
    }

    private String noteOf(AttendanceExceptionNoteRequest request) {
        if (request == null) {
            return null;
        }
        return request.note();
    }
}
