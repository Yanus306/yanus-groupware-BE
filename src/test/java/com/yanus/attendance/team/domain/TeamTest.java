package com.yanus.attendance.team.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TeamTest {

    @Test
    @DisplayName("팀 이름으로 Team을 생성")
    void create_team() {
        // given
        TeamName name = TeamName.BACKEND;

        // when
        Team team = new Team.create(name);

        // then
        assertThat(team.getName()).isEqualTo(TeamName.BACKEND);
        assertThat(team.getCreatedAt()).isNotNull();
    }
}
