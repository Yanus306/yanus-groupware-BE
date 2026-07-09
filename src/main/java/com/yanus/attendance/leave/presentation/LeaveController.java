package com.yanus.attendance.leave.presentation;

import com.yanus.attendance.global.response.ApiResponse;
import com.yanus.attendance.leave.application.LeaveService;
import com.yanus.attendance.leave.application.dto.LeaveCreateCommand;
import com.yanus.attendance.leave.presentation.dto.LeaveCreateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "휴가", description = "휴가 등록, 조회, 승인, 거부")
@RestController
@RequestMapping("/api/v1/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping
    public ResponseEntity<ApiResponse<com.yanus.attendance.leave.presentation.dto.LeaveResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @RequestBody LeaveCreateRequest request) {
        LeaveCreateCommand command = new LeaveCreateCommand(request.category(), request.detail(), request.date());
        com.yanus.attendance.leave.application.dto.LeaveResponse response =
                leaveService.create(memberId, command);
        return ResponseEntity.ok(ApiResponse.success(com.yanus.attendance.leave.presentation.dto.LeaveResponse.from(response)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<com.yanus.attendance.leave.presentation.dto.LeaveResponse>>> getMyLeaveRequests(
            @AuthenticationPrincipal Long memberId) {
        List<com.yanus.attendance.leave.presentation.dto.LeaveResponse> responses = leaveService.getMyLeaveRequests(memberId).stream()
                .map(com.yanus.attendance.leave.presentation.dto.LeaveResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<com.yanus.attendance.leave.presentation.dto.LeaveResponse>>> getTeamLeaveRequests(
            @RequestParam Long teamId) {
        List<com.yanus.attendance.leave.presentation.dto.LeaveResponse> responses = leaveService.getTeamLeaveRequests(teamId).stream()
                .map(com.yanus.attendance.leave.presentation.dto.LeaveResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PatchMapping("/{leaveId}/approve")
    public ResponseEntity<ApiResponse<com.yanus.attendance.leave.presentation.dto.LeaveResponse>> approve(
            @PathVariable Long leaveId,
            @AuthenticationPrincipal Long reviewerId) {
        com.yanus.attendance.leave.application.dto.LeaveResponse response =
                leaveService.approve(leaveId, reviewerId);
        return ResponseEntity.ok(ApiResponse.success(com.yanus.attendance.leave.presentation.dto.LeaveResponse.from(response)));
    }

    @PatchMapping("/{leaveId}/reject")
    public ResponseEntity<ApiResponse<com.yanus.attendance.leave.presentation.dto.LeaveResponse>> reject(
            @PathVariable Long leaveId,
            @AuthenticationPrincipal Long reviewerId) {
        com.yanus.attendance.leave.application.dto.LeaveResponse response =
                leaveService.reject(leaveId, reviewerId);
        return ResponseEntity.ok(ApiResponse.success(com.yanus.attendance.leave.presentation.dto.LeaveResponse.from(response)));
    }
}
