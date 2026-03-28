package com.yanus.attendance.member.application;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberQueryRepository;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.presentation.dto.MemberResponse;
import com.yanus.attendance.member.presentation.dto.ProfileUpdateRequest;
import com.yanus.attendance.member.presentation.dto.RoleChangeRequest;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberQueryRepository memberQueryRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeamRepository teamRepository;

    public MemberResponse findById(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return MemberResponse.from(member);
    }

    public List<MemberResponse> findAll(String teamName, MemberRole role) {
        return memberQueryRepository.findAllByFilter(teamName, role)
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional
    public void changeRole(Long actorId, Long memberId, RoleChangeRequest request) {
        validateAdmin(actorId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.changeRole(request.role());
    }

    @Transactional
    public void deactivate(Long actorId, Long memberId) {
        validateAdmin(actorId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.deactivate();
    }

    @Transactional
    public void activate(Long actorId, Long memberId) {
        validateAdmin(actorId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.activate();
    }

    @Transactional
    public void updateProfile(Long memberId, ProfileUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.updateProfile(request.name(), request.password(), passwordEncoder);
    }

    @Transactional
    public void changeTeam(Long actorId, Long memberId, Long teamId) {
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Member target = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));

        validateTeamChangePermission(actor, target);
        target.changeTeam(team);
    }

    private void validateAdmin(Long actorId) {
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if(actor.getRole() != MemberRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateTeamChangePermission(Member actor, Member target) {
        if (actor.getRole() == MemberRole.ADMIN) {
            return;
        }
        if (actor.getRole() == MemberRole.TEAM_LEAD
                && actor.getTeam().getId().equals(target.getTeam().getId())
                && !target.isInactive()) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
