package com.yanus.attendance.auth.presentation.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {}
