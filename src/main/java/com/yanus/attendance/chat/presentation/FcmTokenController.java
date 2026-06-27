package com.yanus.attendance.chat.presentation;

import com.yanus.attendance.chat.application.FcmTokenService;
import com.yanus.attendance.chat.presentation.dto.DeviceTokenRequest;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "FCM 토큰", description = "푸시 알림용 디바이스 토큰 등록/해제")
@RestController
@RequestMapping("/api/v1/fcm/tokens")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> register(
            @AuthenticationPrincipal Long memberId,
            @RequestBody DeviceTokenRequest request) {
        fcmTokenService.register(memberId, request.token());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unregister(
            @AuthenticationPrincipal Long memberId,
            @RequestParam String token) {
        fcmTokenService.unregister(token);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
