package com.yanus.attendance.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @BeforeEach
    void setUp() {
        memberRepository = new FakeMemberRepository();
        teamRepository = new FakeTeamRepository();
        memberService = new MemberService(memberRepository, memberQueryRepository, new BCryptPasswordEncoder(), teamRepository);
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
        Member member = createMember("hong@yanus.com", MemberRole.MEMBER);
        RoleChangeRequest request = new RoleChangeRequest(MemberRole.TEAM_LEAD);

        // when
        memberService.changeRole(member.getId(), request);

        // then
        assertThat(memberRepository.findById(member.getId()).get().getRole())
                .isEqualTo(MemberRole.TEAM_LEAD);
    }

    @Test
    @DisplayName("멤버 비활성화")
    void deactivate_member() {
        // given
        Member member = createMember("hong@yanus.com", MemberRole.MEMBER);

        // when
        memberService.deactivate(member.getId());

        // then
        assertThat(memberRepository.findById(member.getId()).get().getStatus())
                .isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    @DisplayName("멤버 활성화")
    void activate_member() {
        // given
        Member member = createMember("hong@yanus.com", MemberRole.MEMBER);

        // when
        memberService.activate(member.getId());

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
        Member member = memberRepository.save(
                Member.create("정용태", "jyt@naver.com", "encoded", MemberRole.MEMBER, MemberStatus.ACTIVE, teamA));

        // when
        memberService.changeTeam(member.getId(), teamB.getId());

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
        assertThatThrownBy(() -> memberService.changeTeam(999L, team.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 팀으로 변경 시 예외 발생")
    void does_not_exist_team_error() {
        // given
        Team team = teamRepository.save(Team.create("1팀"));
        Member member = memberRepository.save(
                Member.create("정용태", "jyt@naver.com", "encoded", MemberRole.MEMBER, MemberStatus.ACTIVE, team));

        // when & then`
        assertThatThrownBy(() -> memberService.changeTeam(member.getId(), 999L))
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
        Member actor = createMember("actor@yanus.com", MemberRole.MEMBER);
        Member target = createMember("target@yanus.com", MemberRole.MEMBER);
        memberService.deactivate(actor.getId(), target.getId()); // 이건 나중에 ADMIN으로 바꿔야 하지만 일단 구조만

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
}
