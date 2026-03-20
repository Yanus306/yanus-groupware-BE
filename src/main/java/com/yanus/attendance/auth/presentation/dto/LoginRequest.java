package com.yanus.attendance.auth.presentation.dto;

public record LoginRequest(
        String email,
        String password
) {}
