package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.attendance.FakeAttendanceRepository;
import com.yanus.attendance.attendance.domain.AttendanceRepository;
import com.yanus.attendance.attendance.domain.AttendanceStatus;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
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
        attendanceService = new AttendanceService(attendanceRepository, memberRepository);
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
}
