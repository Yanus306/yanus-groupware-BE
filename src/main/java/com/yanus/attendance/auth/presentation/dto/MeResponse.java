package com.yanus.attendance.auth.presentation.dto;

import com.yanus.attendance.member.domain.Member;

public record MeResponse(
        Long id,
        String name,
        String email,
        String team,
        String role
) {
    public static MeResponse from(Member member) {
        return new MeResponse(
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getTeam().getName(),
                member.getRole().name()
        );
    }
}
