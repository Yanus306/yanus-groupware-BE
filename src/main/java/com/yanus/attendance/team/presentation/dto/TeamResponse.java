package com.yanus.attendance.team.presentation.dto;

import com.yanus.attendance.team.domain.Team;

public record TeamResponse(
        Long id,
        String name
) {
    public static TeamResponse from(Team team) {
        return new TeamResponse(team.getId(), team.getName().name());
    }
}
