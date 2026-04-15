package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.attendance.FakeAttendanceQueryRepository;
import com.yanus.attendance.attendance.FakeAttendanceRepository;
import com.yanus.attendance.attendance.application.attendance.AttendanceService;
import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceQueryRepository;
import com.yanus.attendance.attendance.domain.attendance.AttendanceRepository;
import com.yanus.attendance.attendance.domain.attendance.AttendanceStatus;
import com.yanus.attendance.attendance.presentation.dto.AttendanceRangeResponse;
import com.yanus.attendance.attendance.presentation.dto.AttendanceResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class AttendanceServiceTest {

    private AttendanceService attendanceService;
    private AttendanceRepository attendanceRepository;
    private MemberRepository memberRepository;

    private final AtomicLong memberEmailSeq = new AtomicLong(1);
    private final AtomicLong teamIdSeq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        attendanceRepository = new FakeAttendanceRepository();
        memberRepository = new FakeMemberRepository();
        AttendanceQueryRepository attendanceQueryRepository = new FakeAttendanceQueryRepository(attendanceRepository);
        attendanceService = new AttendanceService(attendanceRepository, memberRepository, attendanceQueryRepository);
        memberEmailSeq.set(1);
        teamIdSeq.set(1);
    }

    private Member createMember() {
        return createMember(MemberRole.ADMIN);
    }

    private Member createMember(MemberRole role) {
        Team team = Team.create("1팀");
        ReflectionTestUtils.setField(team, "id", 1L);
        String email = "user" + memberEmailSeq.getAndIncrement() + "@test.com";
        Member member = Member.create("정용태", email, "password123", role, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    private Member createMemberWithTeam(MemberRole role, Team team) {
        String email = "user" + memberEmailSeq.getAndIncrement() + "@test.com";
        Member member = Member.create("멤버", email, "password123", role, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    private Team createTeam(String name) {
        Team team = Team.create(name);
        ReflectionTestUtils.setField(team, "id", teamIdSeq.getAndIncrement());
        return team;
    }


    @Test
    @DisplayName("출근 체크인 시 WORKING 상태로 저장")
    void check_in() {
        // given
        Member member = createMember();

        // when
        AttendanceResponse response = attendanceService.checkIn(member.getId());

        // then
        assertThat(response.status()).isEqualTo(AttendanceStatus.WORKING);
        assertThat(response.checkInTime()).isNotNull();
        assertThat(response.checkOutTime()).isNull();
    }

    @Test
    @DisplayName("오늘 이미 출근한 경우 예외 발생")
    void already_checked_in_error() {
        // given
        Member member = createMember();
        attendanceService.checkIn(member.getId());

        // when & then
        assertThatThrownBy(() -> attendanceService.checkIn(member.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 출근");
    }

    @Test
    @DisplayName("출근 기록 없이 체크아웃 시 예외 발생")
    void check_out_without_check_in_error() {
        // given
        Member member = createMember();

        // when & then
        assertThatThrownBy(() -> attendanceService.checkOut(member.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("내 출근 기록 조회")
    void get_my_attendances() {
        // given
        Member member = createMember();
        attendanceService.checkIn(member.getId());

        // when
        List<AttendanceResponse> responses = attendanceService.getMyAttendances(member.getId());

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("오늘 출근하는 모든 사람 조회")
    void get_attendances_by_date() {
        // given
        Member member = createMember();
        LocalDate date = LocalDateTime.now().toLocalDate();
        attendanceService.checkIn(member.getId());

        // when
        List<AttendanceResponse> responses = attendanceService.getAttendancesByDate(date);

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("팀 필터로 출퇴근 기록 조회")
    void get_attendances_by_filter_with_team() {
        // given
        Member member = createMember();
        LocalDate date = LocalDate.now();
        attendanceService.checkIn(member.getId());

        // when
        List<AttendanceResponse> responses = attendanceService.getAttendancesByFilter(date, "1팀");

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("팀 필터 없이 전체 출퇴근 기록 조회")
    void get_attendances_by_filter_without_team() {
        // given
        Member member = createMember();
        LocalDate date = LocalDate.now();
        attendanceService.checkIn(member.getId());

        // when
        List<AttendanceResponse> responses = attendanceService.getAttendancesByFilter(date, null);

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("자정 자동 퇴근 처리")
    void auto_check_out() {
        // given
        Member member = createMember();
        LocalDate date = LocalDate.now();
        attendanceService.checkIn(member.getId());

        // when
        attendanceService.autoCheckOut(date);
        List<AttendanceResponse> responses = attendanceService.getMyAttendances(member.getId());

        // then
        assertThat(responses.get(0).status()).isEqualTo(AttendanceStatus.LEFT);
        assertThat(responses.get(0).checkOutTime()).isEqualTo(date.atTime(23, 59, 59));
    }

    @Test
    @DisplayName("허용된 IP로 체크인 성공")
    void check_in_with_allowed_ip() {
        // given
        Member member = createMember();

        // when
        AttendanceResponse response = attendanceService.checkIn(member.getId(), "220.69.1.1");

        // then
        assertThat(response.status()).isEqualTo(AttendanceStatus.WORKING);
    }

    @Test
    @DisplayName("허용되지 않은 IP로 체크인 시 예외 발생")
    void check_in_with_not_allowed_ip() {
        // given
        Member member = createMember();

        // when & then
        assertThatThrownBy(() -> attendanceService.checkIn(member.getId(), "220.32.1.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ATTENDANCE_IP);
    }

    @Test
    @DisplayName("오늘 출근 기록 초기화 성공")
    void reset_attendance_success() {
        // given
        Member member = createMember();
        attendanceService.checkIn(member.getId(), "220.69.1.1");

        // when
        attendanceService.resetAttendance(member.getId(), LocalDate.now());

        // then
        assertThat(attendanceService.getMyAttendances(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("출근 기록 없을 때 초기화 시 ATTENDANCE_NOT_FOUND 예외")
    void reset_attendance_not_found_error() {
        // given
        Member member = createMember();

        // when & then
        assertThatThrownBy(() -> attendanceService.resetAttendance(member.getId(), LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTENDANCE_NOT_FOUND);
    }

    @Test
    @DisplayName("기간별 출근 내역 본인 조회")
    void get_attendances_by_range() {
        // given
        Member member = createMember(MemberRole.MEMBER);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.of(2026, 3, 4, 9, 0, 0));
        attendanceRepository.save(attendance);

        // when
        List<AttendanceRangeResponse> result = attendanceService.getAttendancesByRange(
                member.getId(), member.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).workDate()).isEqualTo(LocalDate.of(2026, 3, 4));
    }

    @Test
    @DisplayName("팀장은 같은 팀 멤버 출근 내역 조회 가능")
    void team_lead_can_get_attendances_by_range() {
        // given
        Team team = createTeam("1팀");
        Member leader = createMemberWithTeam(MemberRole.TEAM_LEAD, team);
        Member target = createMemberWithTeam(MemberRole.MEMBER, team);

        // when & then
        assertThatNoException().isThrownBy(() ->
                attendanceService.getAttendancesByRange(
                        leader.getId(), target.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
    }

    @Test
    @DisplayName("팀장은 다른 팀 멤버 출근 내역 조회 불가")
    void team_lead_cannot_get_attendances_by_range() {
        // given
        Team myTeam = createTeam("1팀");
        Team otherTeam = createTeam("2팀");
        Member leader = createMemberWithTeam(MemberRole.TEAM_LEAD, myTeam);
        Member other = createMemberWithTeam(MemberRole.MEMBER, otherTeam);

        // when & then
        assertThatThrownBy(() ->
                attendanceService.getAttendancesByRange(
                        leader.getId(), other.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("멤버가 타인 출근 내역 조회 시 예외")
    void member_cannot_get_attendances_by_range() {
        // given
        Member me = createMember(MemberRole.MEMBER);
        Member other = createMember(MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() ->
                attendanceService.getAttendancesByRange(
                        me.getId(), other.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .isInstanceOf(BusinessException.class);
    }
}
