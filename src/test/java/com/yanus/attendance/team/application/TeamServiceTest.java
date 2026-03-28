package com.yanus.attendance.team.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.FakeTeamRepository;
import com.yanus.attendance.team.presentation.dto.TeamCreateRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TeamServiceTest {

    private TeamService teamService;
    private FakeTeamRepository teamRepository;
    private FakeMemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        teamRepository = new FakeTeamRepository();
        memberRepository = new FakeMemberRepository();
        teamService = new TeamService(teamRepository, memberRepository);
    }

    private Member createMember(MemberRole role) {
        Team team = teamRepository.save(Team.create("기본팀"));
        return memberRepository.save(
                Member.create("테스터", "test@yanus.com", "encoded", role, MemberStatus.ACTIVE, team));
    }

    @Test
    @DisplayName("팀 이름으로 팀 생성")
    void 팀_생성_성공() {
        // given
        String name = "1팀";
        Member member = createMember(MemberRole.ADMIN);

        // when
        TeamCreateRequest result = teamService.createTeam(member.getId(), name);

        // then
        assertThat(result.name()).isEqualTo("1팀");
    }

    @Test
    @DisplayName("중복 팀 이름 생성 시 예외 발생")
    void 중복_팀_이름_예외() {
        // given
        teamRepository.save(Team.create("1팀"));
        Member member = createMember(MemberRole.ADMIN);

        // when & then
        assertThatThrownBy(() -> teamService.createTeam(member.getId(), "1팀"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEAM_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("멤버 없는 팀 삭제 성공")
    void 팀_삭제_성공() {
        // given
        teamRepository.save(Team.create("빈팀"));
        Member member = createMember(MemberRole.ADMIN);

        // when & then
        assertThatCode(() -> teamService.deleteTeam(member.getId(), 1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("멤버 있는 팀 삭제 시 예외 발생")
    void 멤버_있는_팀_삭제_예외() {
        // given
        FakeTeamRepository fakeRepo = new FakeTeamRepository(true);
        fakeRepo.save(Team.create("1팀"));
        TeamService service = new TeamService(fakeRepo, memberRepository);
        Member member = createMember(MemberRole.ADMIN);

        // when & then
        assertThatThrownBy(() -> service.deleteTeam(member.getId(), 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEAM_HAS_MEMBERS);
    }

    @Test
    @DisplayName("존재하지 않는 팀 삭제 시 예외 발생")
    void 없는_팀_삭제_예외() {
        // given
        Member member = createMember(MemberRole.ADMIN);

        // when & then
        assertThatThrownBy(() -> teamService.deleteTeam(member.getId(), 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEAM_NOT_FOUND);
    }


    @Test
    @DisplayName("전체 팀 목록 조회")
    void find_all() {
        // given
        teamRepository.save(Team.create("1팀"));
        teamRepository.save(Team.create("2팀"));

        // when
        List<TeamCreateRequest> result = teamService.findAll();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("팀 조회")
    void find_by_id() {
        // given
        teamRepository.save(Team.create("2팀"));

        // when
        TeamCreateRequest result = teamService.findById(1L);

        // then
        assertThat(result.name()).isEqualTo("2팀");
    }

    @Test
    @DisplayName("존재하지 않는 팀 ID 조회 시 예외 발생")
    void not_exist_id_error() {
        // given
        Long notExistID = 999L;

        // when & then
        assertThatThrownBy(() -> teamService.findById(notExistID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("ADMIN이 아닌 멤버가 팀 생성 시 예외 발생")
    void admin_is_not_member_error() {
        // given
        Member actor = createMember(MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> teamService.createTeam(actor.getId(), "새팀"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN이 아닌 멤버가 팀 삭제 시 예외 발생")
    void admin_is_not_member_delete_error() {
        // given
        Member actor = createMember(MemberRole.MEMBER);
        Team team = teamRepository.save(Team.create("삭제팀"));

        // when & then
        assertThatThrownBy(() -> teamService.deleteTeam(actor.getId(), team.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN은 팀 생성 성공")
    void admin_is_member_create_success() {
        // given
        Member admin = createMember(MemberRole.ADMIN);

        // when
        TeamCreateRequest result = teamService.createTeam(admin.getId(), "새팀");

        // then
        assertThat(result.name()).isEqualTo("새팀");
    }

    @Test
    @DisplayName("ADMIN은 팀 삭제 성공")
    void admin_is_member_delete_success() {
        // given
        Member admin = createMember(MemberRole.ADMIN);
        Team team = teamRepository.save(Team.create("삭제팀"));

        // when & then
        assertThatCode(() -> teamService.deleteTeam(admin.getId(), team.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("신입팀 삭제 시 예외 발생")
    void newbie_team_can_not_delete() {
        // given
        Member admin = createMember(MemberRole.ADMIN);
        Team juniorTeam = teamRepository.save(Team.create("신입"));

        // when & then
        assertThatThrownBy(() -> teamService.deleteTeam(admin.getId(), juniorTeam.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEAM_CANNOT_DELETE);
    }
}
