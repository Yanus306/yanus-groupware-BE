package com.yanus.attendance.auth.presentation.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
    public static LoginResponse from(com.yanus.attendance.auth.application.dto.AuthTokenResponse response) {
        return new LoginResponse(response.accessToken(), response.refreshToken(), response.tokenType());
    }
}
