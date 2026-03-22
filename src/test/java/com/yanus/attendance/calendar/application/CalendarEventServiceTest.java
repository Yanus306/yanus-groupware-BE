package com.yanus.attendance.calendar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.calendar.FakeCalendarEventRepository;
import com.yanus.attendance.calendar.domain.CalendarEventRepository;
import com.yanus.attendance.calendar.presentation.dto.CalendarEventCreateRequest;
import com.yanus.attendance.calendar.presentation.dto.CalendarEventResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class CalendarEventServiceTest {

    private CalendarEventService calendarEventService;
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        CalendarEventRepository calendarEventRepository = new FakeCalendarEventRepository();
        memberRepository = new FakeMemberRepository();
        calendarEventService = new CalendarEventService(calendarEventRepository, memberRepository);
    }

    private Member createMember() {
        Team team = Team.create(TeamName.BACKEND);
        ReflectionTestUtils.setField(team, "id", 1L);
        Member member = Member.create("정용태", "jyt6640@naver.com", "password123", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    @Test
    @DisplayName("캘린더 이벤트 생성")
    void create_event() {
        // given
        Member member = createMember();
        CalendarEventCreateRequest request = new CalendarEventCreateRequest(
                "스프린트 회의",
                LocalDate.of(2026, 3, 22), LocalTime.of(9, 0),
                LocalDate.of(2026, 3, 22), LocalTime.of(10, 0));

        // when
        CalendarEventResponse response = calendarEventService.create(member.getId(), request);

        // then
        assertThat(response.title()).isEqualTo("스프린트 회의");
    }

    @Test
    @DisplayName("날짜 범위로 이벤트 조회")
    void get_events_by_date_range() {
        // given
        Member member = createMember();
        calendarEventService.create(member.getId(), new CalendarEventCreateRequest(
                "이벤트1", LocalDate.of(2026, 3, 22), LocalTime.of(9, 0),
                LocalDate.of(2026, 3, 22), LocalTime.of(10, 0)));
        calendarEventService.create(member.getId(), new CalendarEventCreateRequest(
                "이벤트2", LocalDate.of(2026, 3, 25), LocalTime.of(9, 0),
                LocalDate.of(2026, 3, 25), LocalTime.of(10, 0)));

        // when
        List<CalendarEventResponse> responses = calendarEventService.getByDateRange(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        // then
        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("생성자 기준 이벤트 조회")
    void get_events_by_created_by() {
        // given
        Member member = createMember();
        calendarEventService.create(member.getId(), new CalendarEventCreateRequest(
                "내 이벤트", LocalDate.of(2026, 3, 22), LocalTime.of(9, 0),
                LocalDate.of(2026, 3, 22), LocalTime.of(10, 0)));

        // when
        List<CalendarEventResponse> responses = calendarEventService.getByCreatedBy(member.getId());

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("이벤트 수정")
    void update_event() {
        // given
        Member member = createMember();
        CalendarEventResponse created = calendarEventService.create(member.getId(),
                new CalendarEventCreateRequest("원래 제목",
                        LocalDate.of(2026, 3, 22), LocalTime.of(9, 0),
                        LocalDate.of(2026, 3, 22), LocalTime.of(10, 0)));

        // when
        CalendarEventResponse response = calendarEventService.update(created.id(),
                new CalendarEventCreateRequest("수정된 제목",
                        LocalDate.of(2026, 3, 23), LocalTime.of(9, 0),
                        LocalDate.of(2026, 3, 23), LocalTime.of(11, 0)));

        // then
        assertThat(response.title()).isEqualTo("수정된 제목");
    }

    @Test
    @DisplayName("이벤트 삭제 후 조회 시 예외 발생")
    void delete_event() {
        // given
        Member member = createMember();
        CalendarEventResponse created = calendarEventService.create(member.getId(),
                new CalendarEventCreateRequest("삭제할 이벤트",
                        LocalDate.of(2026, 3, 22), LocalTime.of(9, 0),
                        LocalDate.of(2026, 3, 22), LocalTime.of(10, 0)));

        // when
        calendarEventService.delete(created.id());

        // then
        assertThatThrownBy(() -> calendarEventService.update(created.id(),
                new CalendarEventCreateRequest("수정", LocalDate.now(), LocalTime.of(9, 0),
                        LocalDate.now(), LocalTime.of(10, 0))))
                .isInstanceOf(BusinessException.class);
    }
}
