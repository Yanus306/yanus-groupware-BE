package com.yanus.attendance.attendance.presentation;

import com.yanus.attendance.attendance.application.AttendanceSettlementService;
import com.yanus.attendance.attendance.presentation.dto.AttendanceSettlementResponse;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지각 정산", description = "월별 지각 정산 조회")
@RestController
@RequestMapping("/api/v1/attendance-settlements")
@RequiredArgsConstructor
public class AttendanceSettlementController {

    private final AttendanceSettlementService settlementService;

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<AttendanceSettlementResponse>> getMonthlySettlement(
            @AuthenticationPrincipal Long memberId,
            @RequestParam String yearMonth,
            @RequestParam(required = false) Long targetMemberId) {

        Long target = targetMemberId != null ? targetMemberId : memberId;

        return ResponseEntity.ok(ApiResponse.success(
                settlementService.getMonthlySettlement(memberId, target, YearMonth.parse(yearMonth))));
    }
}
