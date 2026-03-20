package com.yanus.attendance.auth.presentation.dto;

public record LoginResponse(
        String email,
        String password
) {
}
