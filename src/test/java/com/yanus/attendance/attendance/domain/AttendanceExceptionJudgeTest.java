package com.yanus.attendance.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionJudge;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionType;
import com.yanus.attendance.attendance.domain.workschedule.WeekPattern;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import java.time.DayOfWeek;
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
    }}
