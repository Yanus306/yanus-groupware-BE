package com.yanus.attendance.team.presentation.dto;

import com.yanus.attendance.team.domain.Team;

public record TeamCreateRequest(
        Long id,
        String name
) {
    public static TeamCreateRequest from(Team team) {
        return new TeamCreateRequest(team.getId(), team.getName());
    }
}
