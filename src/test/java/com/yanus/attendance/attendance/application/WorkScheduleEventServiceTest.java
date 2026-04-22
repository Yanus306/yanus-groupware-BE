package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.attendance.FakeWorkScheduleEventRepository;
import com.yanus.attendance.attendance.application.workschedule.WorkScheduleEventService;
import com.yanus.attendance.attendance.presentation.dto.workschedule.WorkScheduleEventRequest;
import com.yanus.attendance.attendance.presentation.dto.workschedule.WorkScheduleEventResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class WorkScheduleEventServiceTest {

    private WorkScheduleEventService workScheduleEventService;
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository = new FakeMemberRepository();
        workScheduleEventService = new WorkScheduleEventService(new FakeWorkScheduleEventRepository(), memberRepository);
    }

    private Member createMember() {
        Team team = Team.create("1팀");
        ReflectionTestUtils.setField(team, "id", 1L);
        Member member = Member.create("테스터", "test@test.com", "password", MemberRole.MEMBER, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    @Test
    @DisplayName("날짜별 근무 일정 생성 성공")
    void create_event_success() {
        // given
        Member member = createMember();
        WorkScheduleEventRequest request = new WorkScheduleEventRequest(
                LocalDate.of(2026, 3, 30), LocalTime.of(9, 0), LocalTime.of(18, 0));

        // when
        WorkScheduleEventResponse response = workScheduleEventService.createEvent(member.getId(), request);

        // then
        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 3, 30));
        assertThat(response.startTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("같은 날짜 중복 일정 생성 시 예외")
    void create_duplicate_event_error() {
        // given
        Member member = createMember();
        WorkScheduleEventRequest request = new WorkScheduleEventRequest(
                LocalDate.of(2026, 3, 30), LocalTime.of(9, 0), LocalTime.of(18, 0));
        workScheduleEventService.createEvent(member.getId(), request);

        // when & then
        assertThatThrownBy(() -> workScheduleEventService.createEvent(member.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORK_SCHEDULE_EVENT_DUPLICATE);
    }

    @Test
    @DisplayName("기간 내 날짜별 일정 조회")
    void get_events_by_period() {
        // given
        Member member = createMember();
        workScheduleEventService.createEvent(member.getId(),
                new WorkScheduleEventRequest(LocalDate.of(2026, 3, 10), LocalTime.of(9, 0), LocalTime.of(18, 0)));
        workScheduleEventService.createEvent(member.getId(),
                new WorkScheduleEventRequest(LocalDate.of(2026, 3, 20), LocalTime.of(9, 0), LocalTime.of(18, 0)));

        // when
        List<WorkScheduleEventResponse> responses = workScheduleEventService.getEvents(
                member.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        // then
        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("날짜별 일정 수정 성공")
    void update_event_success() {
        // given
        Member member = createMember();
        WorkScheduleEventResponse created = workScheduleEventService.createEvent(member.getId(),
                new WorkScheduleEventRequest(LocalDate.of(2026, 3, 30), LocalTime.of(9, 0), LocalTime.of(18, 0)));

        // when
        WorkScheduleEventResponse updated = workScheduleEventService.updateEvent(
                member.getId(), created.id(),
                new WorkScheduleEventRequest(LocalDate.of(2026, 3, 30), LocalTime.of(10, 0), LocalTime.of(19, 0)));

        // then
        assertThat(updated.startTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("날짜별 일정 삭제 성공")
    void delete_event_success() {
        // given
        Member member = createMember();
        WorkScheduleEventResponse created = workScheduleEventService.createEvent(member.getId(),
                new WorkScheduleEventRequest(LocalDate.of(2026, 3, 30), LocalTime.of(9, 0), LocalTime.of(18, 0)));

        // when
        workScheduleEventService.deleteEvent(member.getId(), created.id());

        // then
        List<WorkScheduleEventResponse> responses = workScheduleEventService.getEvents(
                member.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("endsNextDay=false이고 종료 시각이 시작 시간보다 빠르면 생성 실패")
    void create_event_ends_next_day_false_and_end_time_is_earlier_than_start_time() {
        // given
        Member member = createMember();
        WorkScheduleEventRequest request = new WorkScheduleEventRequest(
                LocalDate.of(2026, 4, 21),
                LocalTime.of(23, 0),
                LocalTime.of(1, 0),
                false
        );

        // when & then
        assertThatThrownBy(() -> workScheduleEventService.createEvent(member.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_WORK_SCHEDULE_TIME);
    }
}
