package com.yanus.attendance.member.application.dto;

public record ProfileUpdateCommand(
        String name,
        String password
) {
}
