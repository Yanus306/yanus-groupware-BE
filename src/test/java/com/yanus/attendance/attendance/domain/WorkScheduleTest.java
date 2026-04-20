package com.yanus.attendance.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.attendance.domain.workschedule.WeekPattern;
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
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.MONDAY, start, end, null, false);

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
                WorkSchedule.create(member, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(9, 0), null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("종료 시간");
    }

    @Test
    @DisplayName("근무 일정 수정")
    void update_work_schedule() {
        // given
        WorkSchedule schedule = WorkSchedule.create(createMember(), DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), null, false);

        // when
        schedule.update(LocalTime.of(10, 0), LocalTime.of(19, 0));

        // then
        assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    @DisplayName("야간 근무는 플래그를 통해 생성 가능")
    void night_work_schedule() {
        // given
        Member member = createMember();
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(6, 0);

        // when
        WorkSchedule schedule = WorkSchedule.create(
                member, DayOfWeek.MONDAY, start, end, WeekPattern.EVERY, true);

        // then
        assertThat(schedule.getStartTime()).isEqualTo(start);
        assertThat(schedule.getEndTime()).isEqualTo(end);
        assertThat(schedule.isEndsNextDay()).isTrue();
    }

    @Test
    @DisplayName("같은 날 종료 스케쥴은 endsNextDay_false로 생성")
    void same_day_schedule_ends_next_day_false() {
        // given
        Member member = createMember();

        // when
        WorkSchedule schedule = WorkSchedule.create(
                member, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                WeekPattern.EVERY, false);

        // then
        assertThat(schedule.isEndsNextDay()).isFalse();
    }

    @Test
    @DisplayName("endsNextDay_false면 종료시간이 시작시간보다 빠르면 예외 발생")
    void endsNextDay_false면_종료시간이_시작시간보다_빠르면_예외() {
        // given
        Member member = createMember();

        // when & then
        assertThatThrownBy(() -> WorkSchedule.create(
                member, DayOfWeek.MONDAY,
                LocalTime.of(22, 0), LocalTime.of(6, 0),
                WeekPattern.EVERY, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("야간 근무 수정 시 endsNextDay를 유지할 수 있다")
    void update_overnight_schedule() {
        // given
        WorkSchedule schedule = WorkSchedule.create(createMember(), DayOfWeek.MONDAY,
                LocalTime.of(22, 0), LocalTime.of(6, 0), WeekPattern.EVERY, true);

        // when
        schedule.update(LocalTime.of(23, 0), LocalTime.of(7, 0), true);

        // then
        assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(23, 0));
        assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(schedule.isEndsNextDay()).isTrue();
    }
}
