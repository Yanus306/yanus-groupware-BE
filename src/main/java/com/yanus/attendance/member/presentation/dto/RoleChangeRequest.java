package com.yanus.attendance.member.presentation.dto;

import com.yanus.attendance.member.domain.MemberRole;

public record RoleChangeRequest(
        MemberRole role
) {
}
