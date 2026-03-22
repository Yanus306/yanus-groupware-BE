package com.yanus.attendance.leave.presentation;

import com.yanus.attendance.global.response.ApiResponse;
import com.yanus.attendance.leave.application.LeaveService;
import com.yanus.attendance.leave.presentation.dto.LeaveCreateRequest;
import com.yanus.attendance.leave.presentation.dto.LeaveResponse;
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

@RestController
@RequestMapping("/api/v1/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping
    public ResponseEntity<ApiResponse<LeaveResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @RequestBody LeaveCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.create(memberId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeaveResponse>>> getMyLeaveRequests(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.getMyLeaveRequests(memberId)));
    }

    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<LeaveResponse>>> getTeamLeaveRequests(
            @RequestParam Long teamId) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.getTeamLeaveRequests(teamId)));
    }

    @PatchMapping("/{leaveId}/approve")
    public ResponseEntity<ApiResponse<LeaveResponse>> approve(
            @PathVariable Long leaveId,
            @AuthenticationPrincipal Long reviewerId) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.approve(leaveId, reviewerId)));
    }

    @PatchMapping("/{leaveId}/reject")
    public ResponseEntity<ApiResponse<LeaveResponse>> reject(
            @PathVariable Long leaveId,
            @AuthenticationPrincipal Long reviewerId) {
        return ResponseEntity.ok(ApiResponse.success(leaveService.reject(leaveId, reviewerId)));
    }
}
