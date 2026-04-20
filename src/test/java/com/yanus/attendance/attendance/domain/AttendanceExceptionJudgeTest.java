package com.yanus.attendance.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionJudge;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionType;
import com.yanus.attendance.attendance.domain.workschedule.WeekPattern;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AttendanceExceptionJudgeTest {

    private final AttendanceExceptionJudge judge = new AttendanceExceptionJudge();
    private final Member member = Member.create("홍길동", "hong@test.com", "pw", MemberRole.MEMBER, MemberStatus.ACTIVE, null);

    @Test
    @DisplayName("근무 일정이 있는데 체크인 기록이 없으면 MISSED_CHECK_IN 예외 반환")
    void attend_schedule_but_no_check_in_record() {
        // given
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY);

        // when
        List<AttendanceExceptionType> result = judge.judge(schedule, null, false);

        // then
        assertThat(result).containsExactly(AttendanceExceptionType.MISSED_CHECK_IN);
    }

    @Test
    @DisplayName("근무 일정이 없는데 체크인 기록이 있으면 NO_SCHEDULE 예외를 반환")
    void no_work_schedule_but_chek_in_record_throw_NO_SCHEDULE() {
        // given
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.of(2026, 4, 20, 9, 0));

        // when
        List<AttendanceExceptionType> result = judge.judge(null, attendance, false);

        // then
        assertThat(result).containsExactly(AttendanceExceptionType.NO_SCHEDULE);
    }

    @Test
    @DisplayName("근무 시작 시간 보다 늦게 체크인하면 LATE 예외 반환")
    void work_schedule_time_late_check_in_throw_LATE() {
        // given
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.of(2026, 4, 20, 9, 1));

        // when
        List<AttendanceExceptionType> result = judge.judge(schedule, attendance, false);

        // then
        assertThat(result).containsExactly(AttendanceExceptionType.LATE);
    }

    @Test
    @DisplayName("근무 시작 시간과 정확히 동일하면 LATE 예외 발생하지 않음")
    void work_schedule_time_tie_not_exception() {
        // given
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.of(2026, 4, 20, 9, 0));

        // when
        List<AttendanceExceptionType> result = judge.judge(schedule, attendance, false);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("퇴근이 없고 기준 시각이 지났으면 MISSED_CHECK_OUT 예외 반환")
    void no_check_out_but_time_passed_throw_MISSED_CHECK_OUT() {
        // given
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.of(2026, 4, 20, 9, 0));

        // when
        List<AttendanceExceptionType> result = judge.judge(schedule, attendance, true);

        // then
        assertThat(result).containsExactly(AttendanceExceptionType.MISSED_CHECK_OUT);
    }

    @Test
    @DisplayName("기준 시각이 지나지 않았으면 MISSED_CHECK_OUT 예외 미반환")
    void judge_not_throw_MISSED_CHECK_OUT() {
        // given
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.of(2026, 4, 20, 9, 0));

        // when
        List<AttendanceExceptionType> result = judge.judge(schedule, attendance, false);

        // then
        assertThat(result).doesNotContain(AttendanceExceptionType.MISSED_CHECK_OUT);
    }

    @Test
    @DisplayName("정상 출퇴근은 예외를 반환하지 않음")
    void normal_attendance_not_throw_exception() {
        // given
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.of(2026, 4, 20, 9, 0));
        attendance.checkOut(LocalDateTime.of(2026, 4, 20, 18, 0));

        // when
        List<AttendanceExceptionType> result = judge.judge(schedule, attendance, true);

        // then
        assertThat(result).isEmpty();
    }
}
