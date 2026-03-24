package com.yanus.attendance.leave.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.leave.FakeLeaveRepository;
import com.yanus.attendance.leave.domain.LeaveCategory;
import com.yanus.attendance.leave.domain.LeaveRepository;
import com.yanus.attendance.leave.domain.LeaveStatus;
import com.yanus.attendance.leave.presentation.dto.LeaveCreateRequest;
import com.yanus.attendance.leave.presentation.dto.LeaveResponse;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class LeaveServiceTest {

    private LeaveService leaveService;
    private MemberRepository memberRepository;
    private LeaveRepository leaveRepository;

    @BeforeEach
    void setUp() {
        leaveRepository = new FakeLeaveRepository();
        memberRepository = new FakeMemberRepository();
        leaveService = new LeaveService(leaveRepository, memberRepository);
    }

    private Member createMember() {
        Team team = Team.create("1팀");
        ReflectionTestUtils.setField(team, "id", 1L);
        Member member = Member.create("정용태", "jyt6640@naver.com", "password123", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    @Test
    @DisplayName("휴가 신청 생성")
    void create_leave_request() {
        // given
        Member member = createMember();
        LeaveCreateRequest request = new LeaveCreateRequest(LeaveCategory.VACATION, "연차 사용", LocalDate.now());

        // when
        LeaveResponse response = leaveService.create(member.getId(), request);

        // then
        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
        assertThat(response.category()).isEqualTo(LeaveCategory.VACATION);
    }

    @Test
    @DisplayName("본인 휴가 신청 목록 조회")
    void get_my_leave_requests() {
        // given
        Member member = createMember();
        leaveService.create(member.getId(), new LeaveCreateRequest(LeaveCategory.VACATION, "연차", LocalDate.now()));
        leaveService.create(member.getId(), new LeaveCreateRequest(LeaveCategory.SICK_LEAVE, "병가", LocalDate.now().plusDays(1)));

        // when
        List<LeaveResponse> responses = leaveService.getMyLeaveRequests(member.getId());

        // then
        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("관리자 팀 휴가 신청 목록 조회")
    void get_team_leave_requests() {
        // given
        Member member = createMember();
        leaveService.create(member.getId(), new LeaveCreateRequest(LeaveCategory.VACATION, "연차", LocalDate.now()));

        // when
        List<LeaveResponse> responses = leaveService.getTeamLeaveRequests(1L);

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("휴가 신청 승인")
    void approve_leave_request() {
        // given
        Member member = createMember();
        Member reviewer = memberRepository.save(
                Member.create("리뷰어", "reviewer@naver.com", "password", MemberRole.ADMIN, MemberStatus.ACTIVE,
                        Team.create("1팀")));
        LeaveResponse created = leaveService.create(member.getId(), new LeaveCreateRequest(LeaveCategory.VACATION, "연차", LocalDate.now()));

        // when
        LeaveResponse response = leaveService.approve(created.id(), reviewer.getId());

        // then
        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    @DisplayName("휴가 신청 반려")
    void reject_leave_request() {
        // given
        Member member = createMember();
        Member reviewer = memberRepository.save(
                Member.create("리뷰어", "reviewer@naver.com", "password", MemberRole.ADMIN, MemberStatus.ACTIVE,
                        Team.create("1팀")));
        LeaveResponse created = leaveService.create(member.getId(), new LeaveCreateRequest(LeaveCategory.VACATION, "연차", LocalDate.now()));

        // when
        LeaveResponse response = leaveService.reject(created.id(), reviewer.getId());

        // then
        assertThat(response.status()).isEqualTo(LeaveStatus.REJECTED);
    }

    @Test
    @DisplayName("이미 처리된 신청 승인 시 예외 발생")
    void approve_already_reviewed_error() {
        // given
        Member member = createMember();
        Member reviewer = memberRepository.save(
                Member.create("리뷰어", "reviewer@naver.com", "password", MemberRole.ADMIN, MemberStatus.ACTIVE,
                        Team.create("1팀")));
        LeaveResponse created = leaveService.create(member.getId(), new LeaveCreateRequest(LeaveCategory.VACATION, "연차", LocalDate.now()));
        leaveService.approve(created.id(), reviewer.getId());

        // when & then
        assertThatThrownBy(() -> leaveService.approve(created.id(), reviewer.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 처리");
    }
}
