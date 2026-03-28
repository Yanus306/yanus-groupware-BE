package com.yanus.attendance.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.audit.FakeAuditLogRepository;
import com.yanus.attendance.audit.application.AuditLogService;
import com.yanus.attendance.member.FakeMemberQueryRepository;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.member.presentation.dto.MemberResponse;
import com.yanus.attendance.team.FakeTeamRepository;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class MemberQueryServiceTest {

    private MemberService memberService;
    private FakeTeamRepository teamRepository;
    private FakeMemberRepository memberRepository;
    private FakeMemberQueryRepository memberQueryRepository;
    private AuditLogService auditLogRepository;

    @BeforeEach
    void setUp() {
        memberRepository = new FakeMemberRepository();
        memberQueryRepository = new FakeMemberQueryRepository();
        memberService = new MemberService(memberRepository, memberQueryRepository, new BCryptPasswordEncoder(), teamRepository, auditLogRepository);
    }

    private Member saveMember(String teamName, MemberRole role) {
        Team team = Team.create(teamName);
        Member member = memberRepository.save(
                Member.create("정용태", "jyt6640@naver.com", "encoded", role, MemberStatus.ACTIVE, team));
        memberQueryRepository.save(member);
        return member;
    }

    @Test
    @DisplayName("전체 멤버 조회")
    void find_all_member() {
        // given
        saveMember("1팀", MemberRole.MEMBER);
        saveMember("2팀", MemberRole.MEMBER);

        // when
        List<MemberResponse> result = memberService.findAll(null, null);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("팀으로 필터링")
    void filter_by_team() {
        // given
        saveMember("1팀", MemberRole.MEMBER);
        saveMember("2팀", MemberRole.MEMBER);

        // when
        List<MemberResponse> result = memberService.findAll("1팀", null);

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("역할로 필터링")
    void filter_by_role() {
        // given
        saveMember("1팀", MemberRole.MEMBER);
        saveMember("2팀", MemberRole.ADMIN);
        saveMember("2팀", MemberRole.TEAM_LEAD);

        // when
        List<MemberResponse> result = memberService.findAll("2팀", MemberRole.ADMIN);

        // then
        assertThat(result).hasSize(1);
    }
}
