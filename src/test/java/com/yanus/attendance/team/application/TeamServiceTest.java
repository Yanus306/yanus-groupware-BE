package com.yanus.attendance.team.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import com.yanus.attendance.team.infrastructure.FakeTeamRepository;
import com.yanus.attendance.team.infrastructure.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TeamServiceTest {

    private TeamService teamService;
    private FakeTeamRepository teamRepository;

    @BeforeEach
    void setUp() {
        teamService = new TeamService();
        teamRepository = new TeamRepository();
    }

    @Test
    @DisplayName("팀 목록 조회")
    void find_all() {
        // given
        teamRepository.save(Team.create(TeamName.FRONTEND));
        teamRepository.save(Team.create(TeamName.BACKEND));

        // when
        List<TeamResponse> result = teamService.findAll();

        // then
        assertThat(result).hasSize(2);
    }
}
