package com.yanus.attendance.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class MemberTest {

    private Member createMember() {
        Team team = Team.create(TeamName.BACKEND);
        return Member.create("정용태", "jyt6640@gmail.com", "password", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
    }

    @Test
    @DisplayName("멤버 생성")
    void create_member() {
        // given
        Team team = Team.create(TeamName.BACKEND);
        String name = "정용태";
        String email = "jyt6640@naver.com";
        String encodedPassword = "encoded_password";

        // when
        Member member = Member.create(name, email, encodedPassword, MemberRole.MEMBER, MemberStatus.ACTIVE, team);

        // then
        assertThat(member.getName()).isEqualTo(name);
        assertThat(member.getEmail()).isEqualTo(email);
        assertThat(member.getPassword()).isEqualTo(encodedPassword);
        assertThat(member.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(member.getTeam()).isEqualTo(team);
        assertThat(member.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("멤버 생성시 ACTIVE 상태")
    void when_create_active_member() {
        // given % when
        Member member = createMember();

        // then
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("멤버 비활성화시 INACTIVE 상태")
    void inactive_member() {
        // given
        Member member = createMember();

        // when
        member.deactivate();

        // then
        assertThat(member.getStatus()).isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    @DisplayName("멤버 활성화시 ACTIVE 상태")
    void active_member() {
        // given
        Member member = createMember();

        // when
        member.deactivate();
        member.activate();

        // then
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("멤버 이름 수정")
    void change_name() {
        // given
        Member member = createMember();

        // when
        member.updateProfile("김민성", null);

        // then
        assertThat(member.getName()).isEqualTo("김민성");
    }

    @Test
    @DisplayName("비밀번호 수정")
    void change_password() {
        // given
        Member member = createMember();

        // when
        member.updateProfile(null, "new_password");

        // then
        assertThat(member.getPassword()).isEqualTo("new_password");
    }

    @Test
    @DisplayName("역할 변경")
    void change_role() {
        // given
        Member member = createMember();

        // when
        MemberRole result = member.changeRole(MemberRole.MEMBER);

        // then
        assertThat(result).isEqualTo(MemberRole.MEMBER);
    }
}
