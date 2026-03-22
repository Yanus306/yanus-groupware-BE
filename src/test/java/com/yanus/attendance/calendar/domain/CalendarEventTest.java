package com.yanus.attendance.calendar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CalendarEventTest {

    private Member createMember() {
        Team team = Team.create(TeamName.BACKEND);
        return Member.create("정용태", "jyt6640@naver.com", "password", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
    }

    @Test
    @DisplayName("캘린더 이벤트 생성")
    void create_calendar_event() {
        // given
        Member member = createMember();

        // when
        CalendarEvent event = CalendarEvent.create(member, "스프린트 회의",
                LocalDate.of(2026, 3, 22), LocalTime.of(9, 0),
                LocalDate.of(2026, 3, 22), LocalTime.of(10, 0));

        // then
        assertThat(event.getTitle()).isEqualTo("스프린트 회의");
        assertThat(event.getCreatedBy()).isEqualTo(member);
    }

    @Test
    @DisplayName("종료 일시가 시작 일시보다 이전이면 예외 발생")
    void invalid_end_time() {
        // given
        Member member = createMember();

        // when & then
        assertThatThrownBy(() -> CalendarEvent.create(member, "회의",
                LocalDate.of(2026, 3, 22), LocalTime.of(10, 0),
                LocalDate.of(2026, 3, 22), LocalTime.of(9, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("종료");
    }

    @Test
    @DisplayName("이벤트 수정")
    void update_calendar_event() {
        // given
        Member member = createMember();
        CalendarEvent event = CalendarEvent.create(member, "원래 제목",
                LocalDate.of(2026, 3, 22), LocalTime.of(9, 0),
                LocalDate.of(2026, 3, 22), LocalTime.of(10, 0));

        // when
        event.update("수정된 제목",
                LocalDate.of(2026, 3, 23), LocalTime.of(9, 0),
                LocalDate.of(2026, 3, 23), LocalTime.of(11, 0));

        // then
        assertThat(event.getTitle()).isEqualTo("수정된 제목");
    }
}
