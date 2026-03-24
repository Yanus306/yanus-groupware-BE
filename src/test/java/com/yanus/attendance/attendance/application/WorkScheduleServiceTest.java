package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.attendance.FakeWorkScheduleRepository;
import com.yanus.attendance.attendance.domain.WorkScheduleRepository;
import com.yanus.attendance.attendance.presentation.dto.MemberWorkScheduleResponse;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleRequest;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
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
        Team team = Team.create("1팀");
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

    @Test
    @DisplayName("근무 일정 요일 삭제")
    void delete_work_schedule() {
        // given
        Member member = create();
        workScheduleService.setWorkSchedule(member.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));

        // when
        workScheduleService.deleteWorkSchedule(member.getId(), DayOfWeek.MONDAY);

        // then
        List<WorkScheduleResponse> responses = workScheduleService.getMyWorkSchedules(member.getId());
        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 요일 삭제 시 예외 발생")
    void delete_not_existing_work_schedule() {
        // given
        Member member = create();

        // when & then
        assertThatThrownBy(() -> workScheduleService.deleteWorkSchedule(member.getId(), DayOfWeek.MONDAY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("팀 멤버 근무 일정 조회")
    void get_team_work_schedules() {
        // given
        Member member1 = create();
        Member member2 = memberRepository.save(
                Member.create("김철수", "kim@naver.com", "password", MemberRole.MEMBER, MemberStatus.ACTIVE, member1.getTeam()));

        workScheduleService.setWorkSchedule(member1.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        workScheduleService.setWorkSchedule(member2.getId(),
                new WorkScheduleRequest(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(19, 0)));

        // when
        List<MemberWorkScheduleResponse> responses =
                workScheduleService.getTeamWorkSchedules(member1.getTeam().getId());

        // then
        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("전체 근무 일정 조회 (관리자)")
    void get_all_work_schedules() {
        // given
        Member member1 = create();
        Team team2 = Team.create("2팀");
        ReflectionTestUtils.setField(team2, "id", 2L);
        Member member2 = memberRepository.save(
                Member.create("김철수", "kim@naver.com", "password", MemberRole.MEMBER, MemberStatus.ACTIVE, team2));

        workScheduleService.setWorkSchedule(member1.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        workScheduleService.setWorkSchedule(member2.getId(),
                new WorkScheduleRequest(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));

        // when
        List<MemberWorkScheduleResponse> responses = workScheduleService.getAllWorkSchedules();

        // then
        assertThat(responses).hasSize(2);
    }

}
