package com.yanus.attendance.auth.application.dto;

public record RegisterCommand(
        String name,
        String email,
        String password,
        Long teamId
) {
}
