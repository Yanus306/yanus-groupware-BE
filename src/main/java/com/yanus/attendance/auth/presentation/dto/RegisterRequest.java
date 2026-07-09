package com.yanus.attendance.auth.presentation.dto;

public record RegisterRequest(
        String name,
        String email,
        String password,
        Long teamId
) {
}
