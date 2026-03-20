package com.yanus.attendance.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class MemberServiceTest {

    private MemberService memberService;
    private FakeMemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository = new FakeMemberRepository();
        memberService = new MemberService(memberRepository, new BCryptPasswordEncoder());
    }

    private Member createMember(String email, MemberRole role) {
        Team team = Team.create(TeamName.BACKEND);
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
        Member saved = createMember("hong@yanus.com", MemberRole.MEMBER);
        RoleChangeRequest request = new RoleChangeRequest(MemberRole.TEAM_LEAD);

        // when
        memberService.changeRole(saved.getId(), request);

        // then
        assertThat(memberRepository.findById(saved.getId()).get().getRole())
                .isEqualTo(MemberRole.TEAM_LEAD);
    }

    @Test
    @DisplayName("멤버 비활성화")
    void deactivate_member() {
        // given
        Member saved = createMember("hong@yanus.com", MemberRole.MEMBER);

        // when
        memberService.deactivate(saved.getId());

        // then
        assertThat(memberRepository.findById(saved.getId()).get().getStatus())
                .isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    @DisplayName("프로필 이름 수정")
    void update_profile_name() {
        // given
        Member saved = createMember("hong@yanus.com", MemberRole.MEMBER);
        ProfileUpdateRequest request = new ProfileUpdateRequest("김철수", null);

        // when
        memberService.updateProfile(saved.getId(), request);

        // then
        assertThat(memberRepository.findById(saved.getId()).get().getName())
                .isEqualTo("김철수");
    }

    @Test
    @DisplayName("프로필 비밀번호 수정")
    void update_profile_password() {
        // given
        Member saved = createMember("hong@yanus.com", MemberRole.MEMBER);
        ProfileUpdateRequest request = new ProfileUpdateRequest(null, "newPassword123");

        // when
        memberService.updateProfile(saved.getId(), request);

        // then
        assertThat(memberRepository.findById(saved.getId()).get().getPassword())
                .isNotEqualTo("encoded");
    }
}
