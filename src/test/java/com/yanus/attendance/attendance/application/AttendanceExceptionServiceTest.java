package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.attendance.FakeAttendanceExceptionRepository;
import com.yanus.attendance.attendance.FakeAttendanceRepository;
import com.yanus.attendance.attendance.FakeAttendanceSettingRepository;
import com.yanus.attendance.attendance.FakeWorkScheduleRepository;
import com.yanus.attendance.attendance.application.exception.AttendanceExceptionService;
import com.yanus.attendance.attendance.application.setting.AttendanceSettingService;
import com.yanus.attendance.attendance.domain.exception.AttendanceException;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionType;
import com.yanus.attendance.attendance.domain.workschedule.WeekPattern;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class AttendanceExceptionServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 4, 20);

    private AttendanceExceptionService service;
    private FakeAttendanceExceptionRepository exceptionRepository;
    private FakeAttendanceRepository attendanceRepository;
    private FakeWorkScheduleRepository workScheduleRepository;
    private FakeMemberRepository memberRepository;
    private FakeAttendanceSettingRepository settingRepository;
    private AttendanceSettingService settingService;

    @BeforeEach
    void setUp() {
        exceptionRepository = new FakeAttendanceExceptionRepository();
        attendanceRepository = new FakeAttendanceRepository();
        workScheduleRepository = new FakeWorkScheduleRepository();
        memberRepository = new FakeMemberRepository();
        settingRepository = new FakeAttendanceSettingRepository();
        settingService = new AttendanceSettingService(settingRepository, memberRepository);

        service = new AttendanceExceptionService(
                exceptionRepository,
                attendanceRepository,
                workScheduleRepository,
                memberRepository,
                settingService
        );
    }

    private Member createMember(String name, String email) {
        Team team = Team.create("1팀");
        ReflectionTestUtils.setField(team, "id", 1L);
        Member member = Member.create(name, email, "pw", MemberRole.MEMBER, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    @Test
    @DisplayName("조회 시 해당 날짜의 예외를 판정해 DB에 저장한다")
    void 조회_시_해당_날짜의_예외를_판정해_DB에_저장한다() {
        // given
        Member member = createMember("홍길동", "hong@test.com");
        workScheduleRepository.save(WorkSchedule.create(member, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));

        // when
        service.getExceptions(MONDAY, null, null, null);

        // then
        List<AttendanceException> saved = exceptionRepository.findAllByWorkDate(MONDAY);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getType()).isEqualTo(AttendanceExceptionType.MISSED_CHECK_IN);
    }

    @Test
    @DisplayName("같은 날짜를 두 번 조회해도 예외가 중복 생성되지 않는다")
    void 같은_날짜를_두_번_조회해도_예외가_중복_생성되지_않는다() {
        // given
        Member member = createMember("홍길동", "hong@test.com");
        workScheduleRepository.save(WorkSchedule.create(member, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0), WeekPattern.EVERY));

        // when
        service.getExceptions(MONDAY, null, null, null);
        service.getExceptions(MONDAY, null, null, null);

        // then
        assertThat(exceptionRepository.findAllByWorkDate(MONDAY)).hasSize(1);
    }

    @Test
    @DisplayName("summary는 타입별 카운트를 집계한다")
    void summary는_타입별_카운트를_집계한다() {
        // given
        Member member1 = createMember("홍길동1", "hong1@test.com");
        Member member2 = createMember("홍길동2", "hong2@test.com");
        Member member3 = createMember("홍길동3", "hong3@test.com");

        exceptionRepository.save(AttendanceException.open(
                member1, null, MONDAY, AttendanceExceptionType.MISSED_CHECK_IN));
        exceptionRepository.save(AttendanceException.open(
                member2, null, MONDAY, AttendanceExceptionType.MISSED_CHECK_IN));
        exceptionRepository.save(AttendanceException.open(
                member3, null, MONDAY, AttendanceExceptionType.LATE));

        // when
        Summary summary = service.getSummary(MONDAY);

        // then
        assertThat(summary.totalCount()).isEqualTo(3);
        assertThat(summary.missedCheckInCount()).isEqualTo(2);
        assertThat(summary.lateCount()).isEqualTo(1);
        assertThat(summary.openCount()).isEqualTo(3);
    }
}