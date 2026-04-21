package com.yanus.attendance.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class WorkScheduleTest {

    private Member createMember() {
        Team team = Team.create("1팀");
        return Member.create("정용태", "jyt6640@naver.com", "password", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
    }

    @Test
    @DisplayName("근무 일정 생성")
    void create_work_schedule() {
        // given
        Member member = createMember();
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(18, 0);

        // when
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.MONDAY, start, end, null);

        // then
        assertThat(schedule.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(schedule.getStartTime()).isEqualTo(start);
        assertThat(schedule.getEndTime()).isEqualTo(end);
    }

    @Test
    @DisplayName("종료 시간이 시작 시간보다 이전이면 예외 발생")
    void invalid_work_schedule_time() {
        // given
        Member member = createMember();

        // when & then
        assertThatThrownBy(() ->
                WorkSchedule.create(member, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(9, 0), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("종료 시간");
    }

    @Test
    @DisplayName("근무 일정 수정")
    void update_work_schedule() {
        // given
        WorkSchedule schedule = WorkSchedule.create(createMember(), DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), null);

        // when
        schedule.update(LocalTime.of(10, 0), LocalTime.of(19, 0));

        // then
        assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(19, 0));
    }
}
