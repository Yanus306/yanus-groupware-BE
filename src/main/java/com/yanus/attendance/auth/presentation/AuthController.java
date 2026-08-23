package com.yanus.attendance.auth.presentation;

import com.yanus.attendance.auth.application.AuthService;
import com.yanus.attendance.auth.application.EmailVerificationService;
import com.yanus.attendance.auth.application.dto.AuthTokenResponse;
import com.yanus.attendance.auth.application.dto.LoginCommand;
import com.yanus.attendance.auth.application.dto.RefreshCommand;
import com.yanus.attendance.auth.application.dto.RegisterCommand;
import com.yanus.attendance.auth.presentation.dto.LoginRequest;
import com.yanus.attendance.auth.presentation.dto.LoginResponse;
import com.yanus.attendance.auth.presentation.dto.MeResponse;
import com.yanus.attendance.auth.presentation.dto.RefreshRequest;
import com.yanus.attendance.auth.presentation.dto.RegisterRequest;
import com.yanus.attendance.auth.presentation.dto.ResendVerificationRequest;
import com.yanus.attendance.auth.presentation.dto.VerifyEmailRequest;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "회원가입, 로그인, 리프레쉬, 로그아웃, 확인")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@RequestBody RegisterRequest request) {
        authService.register(new RegisterCommand(request.name(), request.email(), request.password(), request.teamId()));
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        AuthTokenResponse response = authService.login(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(ApiResponse.success(LoginResponse.from(response)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@RequestBody RefreshRequest request) {
        AuthTokenResponse response = authService.refresh(new RefreshCommand(request.refreshToken()));
        return ResponseEntity.ok(ApiResponse.success(LoginResponse.from(response)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal Long memberId) {
        authService.logout(memberId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal Long memberId) {
        com.yanus.attendance.auth.application.dto.MeResponse response = authService.me(memberId);
        return ResponseEntity.ok(ApiResponse.success(MeResponse.from(response)));
    }
}
