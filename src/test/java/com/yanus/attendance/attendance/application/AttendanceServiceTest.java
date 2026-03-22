package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.attendance.FakeAttendanceQueryRepository;
import com.yanus.attendance.attendance.FakeAttendanceRepository;
import com.yanus.attendance.attendance.domain.AttendanceRepository;
import com.yanus.attendance.attendance.domain.AttendanceStatus;
import com.yanus.attendance.attendance.presentation.dto.AttendanceResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class AttendanceServiceTest {

    private AttendanceService attendanceService;
    private AttendanceRepository attendanceRepository;
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        attendanceRepository = new FakeAttendanceRepository();
        memberRepository = new FakeMemberRepository();
        AttendanceQueryRepository attendanceQueryRepository = new FakeAttendanceQueryRepository(attendanceRepository);
        attendanceService = new AttendanceService(attendanceRepository, memberRepository, attendanceQueryRepository);
    }

    private Member createMember() {
        Team team = Team.create(TeamName.BACKEND);
        ReflectionTestUtils.setField(team, "id", 1L);
        Member member = Member.create("정용태", "jyt6640@naver.com", "password123", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
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
        List<AttendanceResponse> responses = attendanceService.getAttendancesByFilter(date, TeamName.BACKEND);

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
}
