package com.yanus.attendance.auth.presentation.dto;

import com.yanus.attendance.team.domain.Team;

public record RegisterRequest(
        String name,
        String email,
        String password,
        Team team
) {}
