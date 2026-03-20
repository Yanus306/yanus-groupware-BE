package com.yanus.attendance.auth.presentation.dto;

public record RefreshRequest(
        String accessToken,
        String refreshToken,
        String tokenType
) {
}
