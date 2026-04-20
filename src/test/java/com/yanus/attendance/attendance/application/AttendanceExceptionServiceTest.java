package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.attendance.FakeAttendanceExceptionRepository;
import com.yanus.attendance.attendance.FakeAttendanceRepository;
import com.yanus.attendance.attendance.FakeAttendanceSettingRepository;
import com.yanus.attendance.attendance.FakeWorkScheduleRepository;
import com.yanus.attendance.attendance.application.exception.AttendanceExceptionService;
import com.yanus.attendance.attendance.application.setting.AttendanceSettingService;
import com.yanus.attendance.attendance.domain.exception.AttendanceException;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionStatus;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionType;
import com.yanus.attendance.attendance.domain.workschedule.WeekPattern;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import com.yanus.attendance.attendance.presentation.dto.AttendanceExceptionSummary;
import com.yanus.attendance.global.exception.BusinessException;
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
        AttendanceExceptionSummary summary = service.getSummary(MONDAY);

        // then
        assertThat(summary.totalCount()).isEqualTo(3);
        assertThat(summary.missedCheckInCount()).isEqualTo(2);
        assertThat(summary.lateCount()).isEqualTo(1);
        assertThat(summary.openCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("approve 호출 시 예외가 APPROVED 상태로 변경된다")
    void approve_change_status_to_APPROVED() {
        // given
        Member member = createMember("홍길동", "hong@test.com");
        AttendanceException saved = exceptionRepository.save(
                AttendanceException.open(member, null, MONDAY, AttendanceExceptionType.LATE));

        // when
        service.approve(saved.getId(), "admin", "승인");

        // then
        AttendanceException updated = exceptionRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AttendanceExceptionStatus.APPROVED);
        assertThat(updated.getApprovedBy()).isEqualTo("admin");
        assertThat(updated.getNote()).isEqualTo("승인");
    }

    @Test
    @DisplayName("reject 호출 시 예외가 REJECTED 상태로 변경된다")
    void reject_change_status_to_REJECTED() {
        // given
        Member member = createMember("홍길동", "hong@test.com");
        AttendanceException saved = exceptionRepository.save(
                AttendanceException.open(member, null, MONDAY, AttendanceExceptionType.LATE));

        // when
        service.reject(saved.getId(), "반려");

        // then
        AttendanceException updated = exceptionRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AttendanceExceptionStatus.REJECTED);
    }

    @Test
    @DisplayName("resolve 호출 시 예외가 RESOLVED 상태로 변경된다")
    void resolve_change_status_to_RESOLVED() {
        // given
        Member member = createMember("홍길동", "hong@test.com");
        AttendanceException saved = exceptionRepository.save(
                AttendanceException.open(member, null, MONDAY, AttendanceExceptionType.MISSED_CHECK_OUT));

        // when
        service.resolve(saved.getId(), "admin", "처리 완료");

        // then
        AttendanceException updated = exceptionRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AttendanceExceptionStatus.RESOLVED);
        assertThat(updated.getResolvedBy()).isEqualTo("admin");
    }

    @Test
    @DisplayName("updateNote 호출 시 메모와 지각사유가 수정된다")
    void updateNote_update_note_and_reason() {
        // given
        Member member = createMember("홍길동", "hong@test.com");
        AttendanceException saved = exceptionRepository.save(
                AttendanceException.open(member, null, MONDAY, AttendanceExceptionType.LATE));

        // when
        service.updateNote(saved.getId(), "새 메모", "지각 사유");

        // then
        AttendanceException updated = exceptionRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getNote()).isEqualTo("새 메모");
        assertThat(updated.getReason()).isEqualTo("지각 사유");
    }

    @Test
    @DisplayName("존재하지 않는 id 로 처리 시 예외 발생")
    void not_exist_id_error() {
        // when & then
        assertThatThrownBy(() -> service.approve(999L, "admin", null))
                .isInstanceOf(BusinessException.class);
    }
}