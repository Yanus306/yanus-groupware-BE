package com.yanus.attendance.attendance.presentation.setting;

import com.yanus.attendance.attendance.application.setting.AttendanceSettingService;
import com.yanus.attendance.attendance.presentation.dto.setting.AutoCheckoutTimeRequest;
import com.yanus.attendance.attendance.presentation.dto.setting.AutoCheckoutTimeResponse;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "자동 체크아웃 시간 설정", description = "자동 체크아웃 시간 설정")
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class AttendanceSettingController {

    private final AttendanceSettingService attendanceSettingService;

    @GetMapping("/auto-checkout-time")
    public ResponseEntity<ApiResponse<AutoCheckoutTimeResponse>> getAutoCheckoutTime() {
        return ResponseEntity.ok(ApiResponse.success(attendanceSettingService.getAutoCheckoutTime()));
    }

    @PatchMapping("/auto-checkout-time")
    public ResponseEntity<ApiResponse<AutoCheckoutTimeResponse>> updateAutoCheckoutTime(
            @AuthenticationPrincipal Long memberId,
            @RequestBody AutoCheckoutTimeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(attendanceSettingService.updateAutoCheckoutTime(memberId, request)));
    }
}
