package com.yanus.attendance.auth.application.dto;

public record LoginCommand(
        String email,
        String password
) {
}
