package com.yanus.attendance.member.application.dto;

import com.yanus.attendance.member.domain.MemberRole;

public record RoleChangeCommand(
        MemberRole role
) {
}
