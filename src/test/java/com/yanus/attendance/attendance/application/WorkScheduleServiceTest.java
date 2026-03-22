package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.attendance.FakeWorkScheduleRepository;
import com.yanus.attendance.attendance.domain.WorkScheduleRepository;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleRequest;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleResponse;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class WorkScheduleServiceTest {

    private WorkScheduleService workScheduleService;
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        WorkScheduleRepository workScheduleRepository = new FakeWorkScheduleRepository();
        memberRepository = new FakeMemberRepository();
        workScheduleService = new WorkScheduleService(workScheduleRepository, memberRepository);
    }

    private Member create() {
        Team team = Team.create(TeamName.BACKEND);
        ReflectionTestUtils.setField(team, "id", 1L);
        Member member = Member.create("정용태", "jyt6640@naver.com", "password123", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    @Test
    @DisplayName("근무 일정 등록")
    void set_work_schedule() {
        // given
        Member member = create();
        WorkScheduleRequest request = new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0));

        // when
        WorkScheduleResponse response = workScheduleService.setWorkSchedule(member.getId(), request);

        // then
        assertThat(response.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(response.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.endTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("같은 요일 근무 일정 재등록 시 업데이트")
    void update_work_schedule_when_same_day() {
        // given
        Member member = create();
        workScheduleService.setWorkSchedule(member.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));

        // when
        WorkScheduleResponse response = workScheduleService.setWorkSchedule(member.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(19, 0)));

        // then
        assertThat(response.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(response.endTime()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    @DisplayName("내 근무 일정 조회")
    void get_my_work_schedules() {
        // given
        Member member = create();
        workScheduleService.setWorkSchedule(member.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        workScheduleService.setWorkSchedule(member.getId(),
                new WorkScheduleRequest(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));

        // when
        List<WorkScheduleResponse> responses = workScheduleService.getMyWorkSchedules(member.getId());

        // then
        assertThat(responses).hasSize(2);
    }
}
