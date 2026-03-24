package com.yanus.attendance.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AttendanceTest {

    private Member create() {
        Team team = Team.create("1팀");
        return Member.create("정용태", "jyt6640@naver.com", "password", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
    }

    @Test
    @DisplayName("출근 시 WORKING 상태로 생성")
    void check_in() {
        // given
        Member member = create();
        LocalDateTime checkInTime = LocalDateTime.now();

        // when
        Attendance attendance = Attendance.checkIn(member, checkInTime);

        // then
        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.WORKING);
        assertThat(attendance.getCheckInTime()).isEqualTo(checkInTime);
        assertThat(attendance.getWorkDate()).isEqualTo(checkInTime.toLocalDate());
        assertThat(attendance.getCheckOutTime()).isNull();
    }

    @Test
    @DisplayName("퇴근 시 LEFT 상태로 변경")
    void check_out() {
        // given
        Member member = create();
        LocalDateTime checkInTime = LocalDateTime.now();
        LocalDateTime checkOutTime = LocalDateTime.now().plusHours(1);
        Attendance attendance = Attendance.checkIn(member, checkInTime);

        // when
        attendance.checkOut(checkOutTime);

        // then
        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.LEFT);
        assertThat(attendance.getCheckOutTime()).isEqualTo(checkOutTime);
    }

    @Test
    @DisplayName("이미 퇴근한 경우 퇴근을 다시 누르면 예외 처리")
    void twice_check_out_error() {
        // given
        Member member = create();
        LocalDateTime checkInTime = LocalDateTime.now();
        LocalDateTime checkOutTime = LocalDateTime.now().plusHours(1);
        Attendance attendance = Attendance.checkIn(member, checkInTime);
        attendance.checkOut(checkOutTime);

        // when & then
        assertThatThrownBy(() -> attendance.checkOut(checkOutTime.plusHours(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("퇴근");
    }

    @Test
    @DisplayName("퇴근시간이 출근시간보다 이전이면 예외 발생")
    void validate_check_out_time() {
        // given
        Member member = create();
        LocalDateTime checkInTime = LocalDateTime.now();
        LocalDateTime checkOutTime = LocalDateTime.now().minusHours(1);
        Attendance attendance = Attendance.checkIn(member, checkInTime);

        //when & then
        assertThatThrownBy(() -> attendance.checkOut(checkOutTime))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("퇴근 시간은 출근 시간");
    }
}
