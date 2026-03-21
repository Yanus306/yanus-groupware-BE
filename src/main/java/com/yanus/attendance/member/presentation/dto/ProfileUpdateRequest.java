package com.yanus.attendance.member.presentation.dto;

public record ProfileUpdateRequest(
        String name,
        String password
) {
}
