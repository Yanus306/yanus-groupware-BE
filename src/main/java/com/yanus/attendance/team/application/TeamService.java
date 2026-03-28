package com.yanus.attendance.team.application;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamRepository;
import com.yanus.attendance.team.presentation.dto.TeamCreateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;

    public List<TeamCreateRequest> findAll() {
        return teamRepository.findAll().stream()
                .map(TeamCreateRequest::from)
                .toList();
    }

    public TeamCreateRequest findById(Long id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return TeamCreateRequest.from(team);
    }

    @Transactional
    public TeamCreateRequest createTeam(Long actorId, String name) {
        validateAdmin(actorId);
        if (teamRepository.findByName(name).isPresent()) {
            throw new BusinessException(ErrorCode.TEAM_ALREADY_EXISTS);
        }
        Team team = Team.create(name);
        return TeamCreateRequest.from(teamRepository.save(team));
    }

    @Transactional
    public void deleteTeam(Long actorId, Long teamId) {
        validateAdmin(actorId);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        if (team.getName().equals("신입")) {
            throw new BusinessException(ErrorCode.TEAM_CANNOT_DELETE);
        }
        if (teamRepository.existsByMembersTeamId(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_HAS_MEMBERS);
        }
        teamRepository.deleteById(teamId);
    }

    private void validateAdmin(Long actorId) {
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (actor.getRole() != MemberRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
