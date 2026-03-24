package com.yanus.attendance.team.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TeamTest {

    @Test
    @DisplayName("팀 이름으로 Team을 생성")
    void create_team() {
        // given
        String name = "1팀";

        // when
        Team team = Team.create(name);

        // then
        assertThat(team.getName()).isEqualTo(name);
        assertThat(team.getCreatedAt()).isNotNull();
    }
}
