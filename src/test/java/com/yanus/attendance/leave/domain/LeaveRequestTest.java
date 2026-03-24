package com.yanus.attendance.leave.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LeaveRequestTest {

    private Member createMember() {
        Team team = Team.create("1팀");
        return Member.create("정용태", "jyt6640@naver.com", "password", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
    }

    @Test
    @DisplayName("휴가 신청 생성 시 PENDING 상태로 생성")
    void create_leave_request() {
        // given
        Member member = createMember();

        // when
        LeaveRequest request = LeaveRequest.create(member, LeaveCategory.VACATION, "연차 사용", LocalDate.now());

        // then
        assertThat(request.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(request.getCategory()).isEqualTo(LeaveCategory.VACATION);
        assertThat(request.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("휴가 신청 승인 시 APPROVED 상태로 변경")
    void approve_leave_request() {
        // given
        Member member = createMember();
        Member reviewer = createMember();
        LeaveRequest request = LeaveRequest.create(member, LeaveCategory.VACATION, "연차 사용", LocalDate.now());

        // when
        request.approve(reviewer);

        // then
        assertThat(request.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(request.getReviewedBy()).isEqualTo(reviewer);
        assertThat(request.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("휴가 신청 반려 시 REJECTED 상태로 변경")
    void reject_leave_request() {
        // given
        Member member = createMember();
        Member reviewer = createMember();
        LeaveRequest request = LeaveRequest.create(member, LeaveCategory.VACATION, "연차 사용", LocalDate.now());

        // when
        request.reject(reviewer);

        // then
        assertThat(request.getStatus()).isEqualTo(LeaveStatus.REJECTED);
    }

    @Test
    @DisplayName("이미 처리된 신청에 승인/반려 시 예외 발생")
    void already_reviewed_error() {
        // given
        Member member = createMember();
        Member reviewer = createMember();
        LeaveRequest request = LeaveRequest.create(member, LeaveCategory.VACATION, "연차 사용", LocalDate.now());
        request.approve(reviewer);

        // when & then
        assertThatThrownBy(() -> request.reject(reviewer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 처리");
    }
}
