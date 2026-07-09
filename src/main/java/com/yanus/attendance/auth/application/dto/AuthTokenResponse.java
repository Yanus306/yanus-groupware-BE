package com.yanus.attendance.auth.application.dto;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
}
