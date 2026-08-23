package com.yanus.attendance.member.presentation.dto;

public record TemporaryPasswordResponse(
        String temporaryPassword
) {

    public static TemporaryPasswordResponse from(com.yanus.attendance.member.application.dto.TemporaryPasswordResponse response) {
        return new TemporaryPasswordResponse(response.temporaryPassword());
    }
}
