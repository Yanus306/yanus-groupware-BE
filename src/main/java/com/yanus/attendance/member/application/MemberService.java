package com.yanus.attendance.member.application;

import com.yanus.attendance.audit.application.AuditLogService;
import com.yanus.attendance.audit.domain.AuditAction;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberQueryRepository;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.presentation.dto.MemberResponse;
import com.yanus.attendance.member.presentation.dto.ProfileUpdateRequest;
import com.yanus.attendance.member.presentation.dto.RoleChangeRequest;
import com.yanus.attendance.member.presentation.dto.TemporaryPasswordResponse;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberQueryRepository memberQueryRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeamRepository teamRepository;
    private final AuditLogService auditLogService;

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
        Member actor = validateAdmin(actorId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        String previousRole = member.getRole().name();
        member.changeRole(request.role());
        auditLogService.log(actorId, actor.getRole(), memberId,
                AuditAction.ROLE_CHANGE, previousRole, request.role().name());
    }

    @Transactional
    public void deactivate(Long actorId, Long memberId) {
        Member actor = validateAdmin(actorId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.deactivate();
        auditLogService.log(actorId, actor.getRole(), memberId,
                AuditAction.DEACTIVATE, "ACTIVE", "INACTIVE");
    }

    @Transactional
    public void activate(Long actorId, Long memberId) {
        Member actor = validateAdmin(actorId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.activate();
        auditLogService.log(actorId, actor.getRole(), memberId,
                AuditAction.ACTIVATE, "INACTIVE", "ACTIVE");
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
        String previousTeam = target.getTeam().getName();
        target.changeTeam(team);
        auditLogService.log(actorId, actor.getRole(), memberId,
                AuditAction.TEAM_CHANGE, previousTeam, team.getName());
    }

    @Transactional
    public TemporaryPasswordResponse resetPassword(Long actorId, Long targetId) {
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Member target = memberRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        String temporaryPassword = generateTemporaryPassword();
        validateAdmin(actorId);
        target.updateProfile(null, temporaryPassword, passwordEncoder);
        auditLogService.log(actorId, actor.getRole(), targetId,
                AuditAction.PASSWORD_RESET, null, null);
        return new TemporaryPasswordResponse(temporaryPassword);
    }

    private Member validateAdmin(Long actorId) {
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (actor.getRole() != MemberRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return actor;
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

    private String generateTemporaryPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
