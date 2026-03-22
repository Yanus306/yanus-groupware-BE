package com.yanus.attendance.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TaskTest {

    private Member createMember() {
        Team team = Team.create(TeamName.BACKEND);
        return Member.create("정용태", "jyt6640@naver.com", "password", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
    }

    @Test
    @DisplayName("개인 Task 생성 시 done = false")
    void create_personal_task() {
        // given
        Member member = createMember();

        // when
        Task task = Task.createPersonal(member, "테스트 작성", LocalDate.now(), LocalTime.of(9, 0), TaskPriority.HIGH);

        // then
        assertThat(task.isDone()).isFalse();
        assertThat(task.isTeamTask()).isFalse();
        assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    @DisplayName("Task 완료 토글")
    void toggle_done() {
        // given
        Member member = createMember();
        Task task = Task.createPersonal(member, "테스트 작성", LocalDate.now(), null, TaskPriority.MEDIUM);

        // when
        task.toggleDone();

        // then
        assertThat(task.isDone()).isTrue();
    }

    @Test
    @DisplayName("Task 완료 후 다시 토글하면 미완료")
    void toggle_done_twice() {
        // given
        Member member = createMember();
        Task task = Task.createPersonal(member, "테스트 작성", LocalDate.now(), null, TaskPriority.MEDIUM);
        task.toggleDone();

        // when
        task.toggleDone();

        // then
        assertThat(task.isDone()).isFalse();
    }

    @Test
    @DisplayName("Task 수정")
    void update_task() {
        // given
        Member member = createMember();
        Task task = Task.createPersonal(member, "원래 제목", LocalDate.now(), null, TaskPriority.LOW);

        // when
        task.update("수정된 제목", LocalDate.now().plusDays(1), LocalTime.of(10, 0), TaskPriority.HIGH);

        // then
        assertThat(task.getTitle()).isEqualTo("수정된 제목");
        assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    @DisplayName("팀 Task 생성")
    void create_team_task() {
        // given
        Member member = createMember();
        Team team = Team.create(TeamName.BACKEND);

        // when
        Task task = Task.createTeam(member, member, team, "팀 작업", LocalDate.now(), null, TaskPriority.MEDIUM);

        // then
        assertThat(task.isTeamTask()).isTrue();
        assertThat(task.getTeam()).isEqualTo(team);
    }
}
