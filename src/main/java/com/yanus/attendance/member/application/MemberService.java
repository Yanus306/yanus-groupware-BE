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
import com.yanus.attendance.team.domain.TeamName;
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

    public MemberResponse findById(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return MemberResponse.from(member);
    }

    public List<MemberResponse> findAll(TeamName teamName, MemberRole role) {
        return memberQueryRepository.findAllByFilter(teamName, role)
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional
    public void changeRole(Long memberId, RoleChangeRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.changeRole(request.role());
    }

    @Transactional
    public void deactivate(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.deactivate();
    }

    @Transactional
    public void activate(Long memberId) {
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
}
