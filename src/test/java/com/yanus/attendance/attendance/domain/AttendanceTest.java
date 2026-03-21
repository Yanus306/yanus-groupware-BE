package com.yanus.attendance.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AttendanceTest {

    private Member create() {
        Team team = Team.create(TeamName.BACKEND);
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
}
