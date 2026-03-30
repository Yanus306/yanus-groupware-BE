package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.attendance.FakeWorkScheduleRepository;
import com.yanus.attendance.attendance.domain.WeekPattern;
import com.yanus.attendance.attendance.domain.WorkScheduleRepository;
import com.yanus.attendance.attendance.presentation.dto.MemberWorkScheduleResponse;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleRequest;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
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
    private long teamIdSeq = 1L;

    @BeforeEach
    void setUp() {
        WorkScheduleRepository workScheduleRepository = new FakeWorkScheduleRepository();
        memberRepository = new FakeMemberRepository();
        workScheduleService = new WorkScheduleService(workScheduleRepository, memberRepository);
    }

    private Member create() {
        return createMember("1팀", MemberRole.ADMIN);
    }

    private Member createMember(String teamName, MemberRole role) {
        Team team = Team.create(teamName);
        ReflectionTestUtils.setField(team, "id", teamIdSeq++);
        Member member = Member.create("테스터", role.name() + teamName + "@yanus.com", "password123", role, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    @Test
    @DisplayName("근무 일정 등록")
    void set_work_schedule() {
        // given
        Member member = create();
        WorkScheduleRequest request = new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0),
                WeekPattern.EVERY);

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
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));

        // when
        WorkScheduleResponse response = workScheduleService.setWorkSchedule(member.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(19, 0), WeekPattern.EVERY));

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
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));
        workScheduleService.setWorkSchedule(member.getId(),
                new WorkScheduleRequest(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(19, 0), WeekPattern.EVERY));

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
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));

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
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));
        workScheduleService.setWorkSchedule(member2.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));

        // when
        List<MemberWorkScheduleResponse> responses =
                workScheduleService.getTeamWorkSchedules(member1.getId(), member1.getTeam().getId());

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
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));
        workScheduleService.setWorkSchedule(member2.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));

        // when
        List<MemberWorkScheduleResponse> responses = workScheduleService.getAllWorkSchedules(member1.getId());

        // then
        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("ADMIN이 아닌 멤버가 전체 근무 일정 조회 시 예외 발생")
    void not_admin_get_all_work_schedules_forbidden() {
        // given
        Member member = createMember("1팀", MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> workScheduleService.getAllWorkSchedules(member.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN은 전체 근무 일정 조회 성공")
    void admin_get_all_work_schedules_success() {
        // given
        Member admin = createMember("1팀", MemberRole.ADMIN);
        workScheduleService.setWorkSchedule(admin.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));

        // when
        List<MemberWorkScheduleResponse> responses = workScheduleService.getAllWorkSchedules(admin.getId());

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("TEAM_LEAD가 자신의 팀 근무 일정 조회 성공")
    void team_lead_get_own_team_work_schedules_success() {
        // given
        Member teamLead = createMember("1팀", MemberRole.TEAM_LEAD);
        workScheduleService.setWorkSchedule(teamLead.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));

        // when
        List<MemberWorkScheduleResponse> responses =
                workScheduleService.getTeamWorkSchedules(teamLead.getId(), teamLead.getTeam().getId());

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("TEAM_LEAD가 다른 팀 근무 일정 조회 시 예외 발생")
    void team_lead_get_other_team_work_schedules_forbidden() {
        // given
        Member teamLead = createMember("1팀", MemberRole.TEAM_LEAD);
        Member otherTeamMember = createMember("2팀", MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> workScheduleService.getTeamWorkSchedules(teamLead.getId(), otherTeamMember.getTeam().getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("MEMBER가 팀 근무 일정 조회 시 예외 발생")
    void member_get_team_work_schedules_forbidden() {
        // given
        Member member = createMember("1팀", MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> workScheduleService.getTeamWorkSchedules(member.getId(), member.getTeam().getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("weekPattern FIRST로 등록 후 조회 시 FIRST 반환")
    void set_work_schedule_with_week_pattern() {
        // given
        Member member = create();
        WorkScheduleRequest request = new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.FIRST);

        // when
        WorkScheduleResponse response = workScheduleService.setWorkSchedule(member.getId(), request);

        // then
        assertThat(response.weekPattern()).isEqualTo(WeekPattern.FIRST);
    }

    @Test
    @DisplayName("weekPattern null로 등록 시 기본값 EVERY")
    void set_work_schedule_default_week_pattern() {
        // given
        Member member = create();
        WorkScheduleRequest request = new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), null);

        // when
        WorkScheduleResponse response = workScheduleService.setWorkSchedule(member.getId(), request);

        // then
        assertThat(response.weekPattern()).isEqualTo(WeekPattern.EVERY);
    }

    @Test
    @DisplayName("같은 팀 MEMBER가 팀 근무 일정 조회 성공")
    void member_get_own_team_work_schedules_success() {
        // given
        Member member = createMember("1팀", MemberRole.MEMBER);
        workScheduleService.setWorkSchedule(member.getId(),
                new WorkScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));

        // when
        List<MemberWorkScheduleResponse> responses =
                workScheduleService.getTeamWorkSchedules(member.getId(), member.getTeam().getId());

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("다른 팀 MEMBER가 팀 근무 일정 조회 시 FORBIDDEN")
    void member_get_other_team_work_schedules_forbidden() {
        // given
        Member member = createMember("1팀", MemberRole.MEMBER);
        Member otherMember = createMember("2팀", MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> workScheduleService.getTeamWorkSchedules(member.getId(), otherMember.getTeam().getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }
}
