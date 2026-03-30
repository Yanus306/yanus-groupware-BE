package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.attendance.FakeAttendanceRepository;
import com.yanus.attendance.attendance.FakeWorkScheduleEventRepository;
import com.yanus.attendance.attendance.FakeWorkScheduleRepository;
import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceRepository;
import com.yanus.attendance.attendance.domain.attendance.AttendanceSettlementStatus;
import com.yanus.attendance.attendance.domain.workschedule.WeekPattern;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEvent;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEventRepository;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleRepository;
import com.yanus.attendance.attendance.presentation.dto.AttendanceSettlementItemResponse;
import com.yanus.attendance.attendance.presentation.dto.AttendanceSettlementResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AttendanceSettlementServiceTest {

    private AttendanceSettlementService settlementService;
    private AttendanceRepository attendanceRepository;
    private WorkScheduleRepository workScheduleRepository;
    private WorkScheduleEventRepository workScheduleEventRepository;
    private MemberRepository memberRepository;

    private final AtomicLong emailSeq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        attendanceRepository = new FakeAttendanceRepository();
        workScheduleRepository = new FakeWorkScheduleRepository();
        workScheduleEventRepository = new FakeWorkScheduleEventRepository();
        memberRepository = new FakeMemberRepository();
        settlementService = new AttendanceSettlementService(
                attendanceRepository, workScheduleRepository,
                workScheduleEventRepository, memberRepository);
        emailSeq.set(1);
    }

    private Member createMember(MemberRole role) {
        Team team = Team.create("1팀");
        ReflectionTestUtils.setField(team, "id", 1L);
        String email = "user" + emailSeq.getAndIncrement() + "@test.com";
        Member member = Member.create("정용태", email, "password123", role, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    private AttendanceSettlementItemResponse findItem(AttendanceSettlementResponse response, LocalDate date) {
        return response.items().stream()
                .filter(i -> i.date().equals(date))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("정시 출근은 ON_TIME이고 지각비 0원")
    void 정시_출근은_ON_TIME이고_지각비_0원() {
        // given - 2026-03-04 (수요일)
        Member member = createMember(MemberRole.MEMBER);
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY);
        workScheduleRepository.save(schedule);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.of(2026, 3, 4, 8, 55, 0));
        attendanceRepository.save(attendance);

        // when
        AttendanceSettlementResponse result = settlementService.getMonthlySettlement(
                member.getId(), member.getId(), YearMonth.of(2026, 3));

        // then
        AttendanceSettlementItemResponse item = findItem(result, LocalDate.of(2026, 3, 4));
        assertThat(item.status()).isEqualTo(AttendanceSettlementStatus.ON_TIME);
        assertThat(item.lateMinutes()).isZero();
        assertThat(item.fee()).isZero();
    }

    @Test
    @DisplayName("지각 7분 59초는 7분으로 계산하고 700원")
    void 지각_7분_59초는_7분으로_계산하고_700원() {
        // given - 2026-03-04 (수요일)
        Member member = createMember(MemberRole.MEMBER);
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY);
        workScheduleRepository.save(schedule);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.of(2026, 3, 4, 9, 7, 59));
        attendanceRepository.save(attendance);

        // when
        AttendanceSettlementResponse result = settlementService.getMonthlySettlement(
                member.getId(), member.getId(), YearMonth.of(2026, 3));

        // then
        AttendanceSettlementItemResponse item = findItem(result, LocalDate.of(2026, 3, 4));
        assertThat(item.status()).isEqualTo(AttendanceSettlementStatus.LATE);
        assertThat(item.lateMinutes()).isEqualTo(7);
        assertThat(item.fee()).isEqualTo(700);
    }

    @Test
    @DisplayName("WorkScheduleEvent가 있으면 반복 일정보다 우선 적용")
    void WorkScheduleEvent가_있으면_반복_일정보다_우선_적용() {
        // given - 반복 일정 09:00, 이벤트로 10:00 오버라이드, 10:05 출근
        Member member = createMember(MemberRole.MEMBER);
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY);
        workScheduleRepository.save(schedule);
        WorkScheduleEvent event = WorkScheduleEvent.create(member,
                LocalDate.of(2026, 3, 4), LocalTime.of(10, 0), LocalTime.of(19, 0));
        workScheduleEventRepository.save(event);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.of(2026, 3, 4, 10, 5, 0));
        attendanceRepository.save(attendance);

        // when
        AttendanceSettlementResponse result = settlementService.getMonthlySettlement(
                member.getId(), member.getId(), YearMonth.of(2026, 3));

        // then - 이벤트 기준 10:00 대비 5분 지각
        AttendanceSettlementItemResponse item = findItem(result, LocalDate.of(2026, 3, 4));
        assertThat(item.scheduledStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(item.lateMinutes()).isEqualTo(5);
        assertThat(item.status()).isEqualTo(AttendanceSettlementStatus.LATE);
    }

    @Test
    @DisplayName("근무 일정 없는 날은 items에 포함되지 않는다")
    void 근무_일정_없는_날은_items에_포함되지_않는다() {
        // given - 근무 일정 없음
        Member member = createMember(MemberRole.MEMBER);

        // when
        AttendanceSettlementResponse result = settlementService.getMonthlySettlement(
                member.getId(), member.getId(), YearMonth.of(2026, 3));

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.scheduledDays()).isZero();
    }

    @Test
    @DisplayName("출근 기록 없는 날은 ABSENT이고 지각비 없음")
    void 출근_기록_없는_날은_ABSENT이고_지각비_없음() {
        // given - 일정은 있고 출근 기록 없음
        Member member = createMember(MemberRole.MEMBER);
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY);
        workScheduleRepository.save(schedule);

        // when
        AttendanceSettlementResponse result = settlementService.getMonthlySettlement(
                member.getId(), member.getId(), YearMonth.of(2026, 3));

        // then
        AttendanceSettlementItemResponse item = findItem(result, LocalDate.of(2026, 3, 4));
        assertThat(item.status()).isEqualTo(AttendanceSettlementStatus.ABSENT);
        assertThat(item.fee()).isZero();
        assertThat(result.lateFee()).isZero();
    }

    @Test
    @DisplayName("월별 집계 정상 계산")
    void 월별_집계_정상_계산() {
        // given - 3/4 (수) 10분 지각, 3/11 (수) 정시 출근
        Member member = createMember(MemberRole.MEMBER);
        WorkSchedule schedule = WorkSchedule.create(member, DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY);
        workScheduleRepository.save(schedule);
        attendanceRepository.save(Attendance.checkIn(member, LocalDateTime.of(2026, 3, 4, 9, 10, 0)));
        attendanceRepository.save(Attendance.checkIn(member, LocalDateTime.of(2026, 3, 11, 9, 0, 0)));

        // when
        AttendanceSettlementResponse result = settlementService.getMonthlySettlement(
                member.getId(), member.getId(), YearMonth.of(2026, 3));

        // then
        assertThat(result.lateDays()).isEqualTo(1);
        assertThat(result.totalLateMinutes()).isEqualTo(10);
        assertThat(result.lateFee()).isEqualTo(1000);
        assertThat(result.attendedDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("MEMBER가 타인 정산 조회 시 예외")
    void MEMBER가_타인_정산_조회_시_예외() {
        // given
        Member me = createMember(MemberRole.MEMBER);
        Member other = createMember(MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() ->
                settlementService.getMonthlySettlement(
                        me.getId(), other.getId(), YearMonth.of(2026, 3)))
                .isInstanceOf(BusinessException.class);
    }
}
