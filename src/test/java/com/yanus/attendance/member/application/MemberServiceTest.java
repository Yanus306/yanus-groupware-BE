package com.yanus.attendance.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.audit.FakeAuditLogRepository;
import com.yanus.attendance.audit.application.AuditLogService;
import com.yanus.attendance.audit.domain.AuditAction;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.FakeMemberQueryRepository;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.member.presentation.dto.MemberResponse;
import com.yanus.attendance.member.presentation.dto.ProfileUpdateRequest;
import com.yanus.attendance.member.presentation.dto.RoleChangeRequest;
import com.yanus.attendance.member.presentation.dto.TemporaryPasswordResponse;
import com.yanus.attendance.team.FakeTeamRepository;
import com.yanus.attendance.team.domain.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class MemberServiceTest {

    private MemberService memberService;
    private FakeMemberQueryRepository memberQueryRepository;
    private FakeMemberRepository memberRepository;
    private FakeTeamRepository teamRepository;
    private FakeAuditLogRepository auditLogRepository;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        memberRepository = new FakeMemberRepository();
        teamRepository = new FakeTeamRepository();
        auditLogRepository = new FakeAuditLogRepository();
        auditLogService = new AuditLogService(auditLogRepository);
        memberService = new MemberService(memberRepository, memberQueryRepository, new BCryptPasswordEncoder(), teamRepository, auditLogService);
    }

    private Member createMember(String email, MemberRole role) {
        Team team = Team.create("1팀");
        return memberRepository.save(Member.create("정용태", email, "encoded", role, MemberStatus.ACTIVE, team));
    }

    @Test
    @DisplayName("이메일로 멤버 조회")
    void find_by_email() {
        // given
        Member member = createMember("jyt6640@naver.com", MemberRole.MEMBER);

        // when
        MemberResponse result = memberService.findById(member.getId());

        // then
        assertThat(result.email()).isEqualTo("jyt6640@naver.com");
    }

    @Test
    @DisplayName("존재하지 않는 멤버 조회시 예외 발생")
    void not_exist_member_error() {
        // given
        Long notExistId = 999L;

        // when & then
        assertThatThrownBy(() -> memberService.findById(notExistId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버 역할 변경")
    void change_role() {
        // given
        Member actorId = createMember("asdasd@asdasd.com", MemberRole.ADMIN);
        Member member = createMember("hong@yanus.com", MemberRole.MEMBER);
        RoleChangeRequest request = new RoleChangeRequest(MemberRole.TEAM_LEAD);

        // when
        memberService.changeRole(actorId.getId(), member.getId(), request);

        // then
        assertThat(memberRepository.findById(member.getId()).get().getRole())
                .isEqualTo(MemberRole.TEAM_LEAD);
    }

    @Test
    @DisplayName("멤버 비활성화")
    void deactivate_member() {
        // given
        Member actorId = createMember("asdasd@asdasd.com", MemberRole.ADMIN);
        Member member = createMember("hong@yanus.com", MemberRole.MEMBER);

        // when
        memberService.deactivate(actorId.getId(), member.getId());

        // then
        assertThat(memberRepository.findById(member.getId()).get().getStatus())
                .isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    @DisplayName("멤버 활성화")
    void activate_member() {
        // given
        Member member = createMember("hong@yanus.com", MemberRole.MEMBER);
        Member actorId = createMember("asdasd@asdasd.com", MemberRole.ADMIN);

        // when
        memberService.activate(actorId.getId(), member.getId());

        // then
        assertThat(memberRepository.findById(member.getId()).get().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("프로필 이름 수정")
    void update_profile_name() {
        // given
        Member member = createMember("hong@yanus.com", MemberRole.MEMBER);
        ProfileUpdateRequest request = new ProfileUpdateRequest("김철수", null);

        // when
        memberService.updateProfile(member.getId(), request);

        // then
        assertThat(memberRepository.findById(member.getId()).get().getName())
                .isEqualTo("김철수");
    }

    @Test
    @DisplayName("프로필 비밀번호 수정")
    void update_profile_password() {
        // given
        Member member = createMember("hong@yanus.com", MemberRole.MEMBER);
        ProfileUpdateRequest request = new ProfileUpdateRequest(null, "newPassword123");

        // when
        memberService.updateProfile(member.getId(), request);

        // then
        assertThat(memberRepository.findById(member.getId()).get().getPassword())
                .isNotEqualTo("encoded");
    }

    @Test
    @DisplayName("멤버 팀 변경")
    void change_member_team() {
        // given
        Team teamA = teamRepository.save(Team.create("1팀"));
        Team teamB = teamRepository.save(Team.create("2팀"));
        Member actorId = memberRepository.save(
                Member.create("정용태", "jyt21@naver.com", "encoded", MemberRole.ADMIN, MemberStatus.ACTIVE, teamA));
        Member member = memberRepository.save(
                Member.create("정용태", "jyt@naver.com", "encoded", MemberRole.MEMBER, MemberStatus.ACTIVE, teamA));

        // when
        memberService.changeTeam(actorId.getId(), member.getId(), teamB.getId());

        // then
        Member updated = memberRepository.findById(member.getId()).get();
        assertThat(updated.getTeam().getId()).isEqualTo(teamB.getId());
    }

    @Test
    @DisplayName("존재하지 않는 멤버 팀 변경 시 예외 발생")
    void does_not_exist_member_chang_team_error() {
        // given
        Team team = teamRepository.save(Team.create("1팀"));

        // when & then
        assertThatThrownBy(() -> memberService.changeTeam(999L, 120L, team.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 팀으로 변경 시 예외 발생")
    void does_not_exist_team_error() {
        // given
        Team team = teamRepository.save(Team.create("1팀"));
        Member admin = createMember("asdasd@naver.com", MemberRole.ADMIN);
        Member member = memberRepository.save(
                Member.create("정용태", "jyt@naver.com", "encoded", MemberRole.MEMBER, MemberStatus.ACTIVE, team));

        // when & then`
        assertThatThrownBy(() -> memberService.changeTeam(admin.getId(), member.getId(), 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("ADMIN이 아닌 멤버가 비활성화 시도 시 예외 발생")
    void not_admin_member_deactivate_error() {
        // given
        Member actor = createMember("actor@yanus.com", MemberRole.MEMBER);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> memberService.deactivate(actor.getId(), target.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN이 아닌 멤버가 활성화 시도 시 예외 발생")
    void not_admin_member_activate_error() {
        // given
        Member admin = createMember("asdasd@naver.com", MemberRole.ADMIN);
        Member actor = createMember("actor@yanus.com", MemberRole.MEMBER);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);
        memberService.deactivate(admin.getId(), target.getId());

        // when & then
        assertThatThrownBy(() -> memberService.activate(actor.getId(), target.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN은 역할 변경 가능")
    void admin_can_change_role() {
        // given
        Member admin = createMember("admin@yanus.com", MemberRole.ADMIN);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);
        RoleChangeRequest request = new RoleChangeRequest(MemberRole.TEAM_LEAD);

        // when
        memberService.changeRole(admin.getId(), target.getId(), request);

        // then
        assertThat(memberRepository.findById(target.getId()).get().getRole())
                .isEqualTo(MemberRole.TEAM_LEAD);
    }

    @Test
    @DisplayName("ADMIN은 비활성화 가능")
    void admin_can_deactivate_member() {
        // given
        Member admin = createMember("admin@yanus.com", MemberRole.ADMIN);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);

        // when
        memberService.deactivate(admin.getId(), target.getId());

        // then
        assertThat(memberRepository.findById(target.getId()).get().getStatus())
                .isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    @DisplayName("ADMIN은 활성화 가능")
    void admin_can_activate_member() {
        // given
        Member admin = createMember("admin@yanus.com", MemberRole.ADMIN);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);

        // when
        memberService.activate(admin.getId(), target.getId());

        // then
        assertThat(memberRepository.findById(target.getId()).get().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("TEAM_LEAD가 같은 팀 활성 멤버 팀 변경 성공")
    void team_lead_can_change_team() {
        // given
        Team teamA = teamRepository.save(Team.create("1팀"));
        Team teamB = teamRepository.save(Team.create("2팀"));
        Member teamLead = memberRepository.save(
                Member.create("팀장", "lead@yanus.com", "encoded", MemberRole.TEAM_LEAD, MemberStatus.ACTIVE, teamA));
        Member target = memberRepository.save(
                Member.create("팀원", "member@yanus.com", "encoded", MemberRole.MEMBER, MemberStatus.ACTIVE, teamA));

        // when
        memberService.changeTeam(teamLead.getId(), target.getId(), teamB.getId());

        // then
        assertThat(memberRepository.findById(target.getId()).get().getTeam().getId())
                .isEqualTo(teamB.getId());
    }

    @Test
    @DisplayName("TEAM_LEAD가 다른 팀 멤버 팀 변경 시 예외 발생")
    void team_lead_can_not_change_other_team_error() {
        // given
        Team teamA = teamRepository.save(Team.create("1팀"));
        Team teamB = teamRepository.save(Team.create("2팀"));
        Team teamC = teamRepository.save(Team.create("3팀"));
        Member teamLead = memberRepository.save(
                Member.create("팀장", "lead@yanus.com", "encoded", MemberRole.TEAM_LEAD, MemberStatus.ACTIVE, teamA));
        Member target = memberRepository.save(
                Member.create("팀원", "member@yanus.com", "encoded", MemberRole.MEMBER, MemberStatus.ACTIVE, teamB));

        // when & then
        assertThatThrownBy(() -> memberService.changeTeam(teamLead.getId(), target.getId(), teamC.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("TEAM_LEAD가 비활성 멤버 팀 변경 시 예외 발생")
    void team_lead_can_not_change_inactive_member_error() {
        // given
        Team teamA = teamRepository.save(Team.create("1팀"));
        Team teamB = teamRepository.save(Team.create("2팀"));
        Member teamLead = memberRepository.save(
                Member.create("팀장", "lead@yanus.com", "encoded", MemberRole.TEAM_LEAD, MemberStatus.ACTIVE, teamA));
        Member target = memberRepository.save(
                Member.create("팀원", "member@yanus.com", "encoded", MemberRole.MEMBER, MemberStatus.INACTIVE, teamA));

        // when & then
        assertThatThrownBy(() -> memberService.changeTeam(teamLead.getId(), target.getId(), teamB.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("MEMBER가 팀 변경 시도 시 예외 발생")
    void member_can_not_change_team_error() {
        // given
        Team teamA = teamRepository.save(Team.create("1팀"));
        Team teamB = teamRepository.save(Team.create("2팀"));
        Member actor = memberRepository.save(
                Member.create("일반", "member@yanus.com", "encoded", MemberRole.MEMBER, MemberStatus.ACTIVE, teamA));
        Member target = memberRepository.save(
                Member.create("팀원", "target@yanus.com", "encoded", MemberRole.MEMBER, MemberStatus.ACTIVE, teamA));

        // when & then
        assertThatThrownBy(() -> memberService.changeTeam(actor.getId(), target.getId(), teamB.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("역할 변경 시 감사 로그 저장")
    void 역할_변경_시_감사_로그_저장() {
        // given
        Member admin = createMember("admin@yanus.com", MemberRole.ADMIN);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);
        RoleChangeRequest request = new RoleChangeRequest(MemberRole.TEAM_LEAD);

        // when
        memberService.changeRole(admin.getId(), target.getId(), request);

        // then
        assertThat(auditLogRepository.findAll()).hasSize(1);
        assertThat(auditLogRepository.findAll().get(0).getAction()).isEqualTo(AuditAction.ROLE_CHANGE);
    }

    @Test
    @DisplayName("비활성화 시 감사 로그 저장")
    void 비활성화_시_감사_로그_저장() {
        // given
        Member admin = createMember("admin@yanus.com", MemberRole.ADMIN);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);

        // when
        memberService.deactivate(admin.getId(), target.getId());

        // then
        assertThat(auditLogRepository.findAll()).hasSize(1);
        assertThat(auditLogRepository.findAll().get(0).getAction()).isEqualTo(AuditAction.DEACTIVATE);
    }

    @Test
    @DisplayName("팀 변경 시 감사 로그 저장")
    void 팀_변경_시_감사_로그_저장() {
        // given
        Team teamA = teamRepository.save(Team.create("1팀"));
        Team teamB = teamRepository.save(Team.create("2팀"));
        Member admin = memberRepository.save(
                Member.create("관리자", "admin@yanus.com", "encoded", MemberRole.ADMIN, MemberStatus.ACTIVE, teamA));
        Member target = memberRepository.save(
                Member.create("팀원", "target@yanus.com", "encoded", MemberRole.MEMBER, MemberStatus.ACTIVE, teamA));

        // when
        memberService.changeTeam(admin.getId(), target.getId(), teamB.getId());

        // then
        assertThat(auditLogRepository.findAll()).hasSize(1);
        assertThat(auditLogRepository.findAll().get(0).getAction()).isEqualTo(AuditAction.TEAM_CHANGE);
    }

    @Test
    @DisplayName("관리자가 임시 비밀번호 발급")
    void 관리자가_임시_비밀번호_발급() {
        // given
        Member admin = createMember("admin@yanus.com", MemberRole.ADMIN);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);

        // when
        TemporaryPasswordResponse response = memberService.resetPassword(admin.getId(), target.getId());

        // then
        assertThat(response.temporaryPassword()).hasSize(8);
    }

    @Test
    @DisplayName("임시 비밀번호 발급 후 변경된 비밀번호로 인코딩 저장")
    void 임시_비밀번호_발급_후_인코딩_저장() {
        // given
        Member admin = createMember("admin@yanus.com", MemberRole.ADMIN);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);
        String originalPassword = target.getPassword();

        // when
        memberService.resetPassword(admin.getId(), target.getId());

        // then
        assertThat(memberRepository.findById(target.getId()).get().getPassword())
                .isNotEqualTo(originalPassword);
    }

    @Test
    @DisplayName("ADMIN이 아니면 임시 비밀번호 발급 시 FORBIDDEN")
    void ADMIN이_아니면_임시_비밀번호_발급_시_FORBIDDEN() {
        // given
        Member actor = createMember("actor@yanus.com", MemberRole.MEMBER);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> memberService.resetPassword(actor.getId(), target.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("존재하지 않는 멤버 임시 비밀번호 발급 시 MEMBER_NOT_FOUND")
    void 존재하지_않는_멤버_임시_비밀번호_발급_시_MEMBER_NOT_FOUND() {
        // given
        Member admin = createMember("admin@yanus.com", MemberRole.ADMIN);

        // when & then
        assertThatThrownBy(() -> memberService.resetPassword(admin.getId(), 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("임시 비밀번호 발급 시 감사 로그 저장")
    void 임시_비밀번호_발급_시_감사_로그_저장() {
        // given
        Member admin = createMember("admin@yanus.com", MemberRole.ADMIN);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);

        // when
        memberService.resetPassword(admin.getId(), target.getId());

        // then
        assertThat(auditLogRepository.findAll()).hasSize(1);
        assertThat(auditLogRepository.findAll().get(0).getAction())
                .isEqualTo(AuditAction.PASSWORD_RESET);
    }
}
