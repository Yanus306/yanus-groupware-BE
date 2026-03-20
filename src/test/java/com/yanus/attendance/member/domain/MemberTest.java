package com.yanus.attendance.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class MemberTest {

    @Test
    @DisplayName("멤버 생성") {
        // given
        Team team = Team.create(TeamName.BACKEND);
        String name = "정용태";
        String email = "jyt6640@naver.com";
        String encodedPassword = "encoded_password";

        // when
        Member member = new Member.create(name, email, encodedPassword, MemberRole.MEMBER, team);

        // then
        assertThat(member.getName()).isEqualTo(name);
        assertThat(member.getEmail()).isEqualTo(email);
        assertThat(member.getPassword()).isEqualTo(encodedPassword);
        assertThat(member.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(member.getTeam()).isEqualTo(team);
        assertThat(member.getCreatedAt()).isNotNull();
    }
}
