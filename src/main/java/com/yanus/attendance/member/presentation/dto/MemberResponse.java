package com.yanus.attendance.member.presentation.dto;

import com.yanus.attendance.member.domain.Member;

public record MemberResponse(
        Long id,
        String name,
        String email,
        String role,
        String status,
        String team
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getRole().name(),
                member.getStatus().name(),
                member.getTeam().getName().name()
        );
    }
}
