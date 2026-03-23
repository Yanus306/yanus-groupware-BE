package com.yanus.attendance.leave.application;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.leave.domain.LeaveRepository;
import com.yanus.attendance.leave.domain.LeaveRequest;
import com.yanus.attendance.leave.presentation.dto.LeaveCreateRequest;
import com.yanus.attendance.leave.presentation.dto.LeaveResponse;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final MemberRepository memberRepository;

    public LeaveResponse create(Long memberId, LeaveCreateRequest request) {
        Member member = findMember(memberId);
        LeaveRequest leaveRequest = LeaveRequest.create(member, request.category(), request.detail(), request.date());
        leaveRepository.save(leaveRequest);
        return LeaveResponse.from(leaveRequest);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> getMyLeaveRequests(Long memberId) {
        return leaveRepository.findAllByMemberId(memberId).stream()
                .map(LeaveResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> getTeamLeaveRequests(Long teamId) {
        return leaveRepository.findAllByTeamId(teamId).stream()
                .map(LeaveResponse::from)
                .toList();
    }

    public LeaveResponse approve(Long leaveRequestId, Long reviewerId) {
        LeaveRequest leaveRequest = findLeaveRequest(leaveRequestId);
        Member reviewer = findMember(reviewerId);
        leaveRequest.approve(reviewer);
        return LeaveResponse.from(leaveRequest);
    }

    public LeaveResponse reject(Long leaveRequestId, Long reviewerId) {
        LeaveRequest leaveRequest = findLeaveRequest(leaveRequestId);
        Member reviewer = findMember(reviewerId);
        leaveRequest.reject(reviewer);
        return LeaveResponse.from(leaveRequest);
    }

    private LeaveRequest findLeaveRequest(Long id) {
        return leaveRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_REQUEST_NOT_FOUND));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
