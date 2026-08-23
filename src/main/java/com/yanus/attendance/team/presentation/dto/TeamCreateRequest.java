package com.yanus.attendance.team.presentation.dto;

import com.yanus.attendance.team.application.dto.TeamResponse;

public record TeamCreateRequest(
        Long id,
        String name
) {
    public static TeamCreateRequest from(TeamResponse teamResponse) {
        return new TeamCreateRequest(teamResponse.id(), teamResponse.name());
    }
}
