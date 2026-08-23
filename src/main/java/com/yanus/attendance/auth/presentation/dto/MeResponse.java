package com.yanus.attendance.auth.presentation.dto;

public record MeResponse(
        Long id,
        String name,
        String email,
        String team,
        String role
) {
    public static MeResponse from(com.yanus.attendance.auth.application.dto.MeResponse response) {
        return new MeResponse(response.id(), response.name(), response.email(), response.team(), response.role());
    }
}
