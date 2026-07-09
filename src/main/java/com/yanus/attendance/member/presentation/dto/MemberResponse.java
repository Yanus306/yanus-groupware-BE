package com.yanus.attendance.member.presentation.dto;

public record MemberResponse(
        Long id,
        String name,
        String email,
        String role,
        String status,
        String team
) {
    public static MemberResponse from(com.yanus.attendance.member.application.dto.MemberResponse response) {
        return new MemberResponse(response.id(), response.name(), response.email(), response.role(), response.status(), response.team());
    }
}
