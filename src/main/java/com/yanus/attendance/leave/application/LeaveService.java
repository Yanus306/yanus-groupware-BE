package com.yanus.attendance.leave.application;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.leave.domain.LeaveRepository;
import com.yanus.attendance.leave.domain.LeaveRequest;
import com.yanus.attendance.leave.domain.LeaveCategory;
import com.yanus.attendance.leave.application.dto.LeaveCreateCommand;
import com.yanus.attendance.leave.application.dto.LeaveResponse;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final MemberRepository memberRepository;

    public LeaveResponse create(Long memberId, LeaveCreateCommand request) {
        Member member = findMember(memberId);
        LeaveCategory category = parseEnum(request.category(), LeaveCategory.class);
        LeaveRequest leaveRequest = LeaveRequest.create(member, category, request.detail(), request.date());
        leaveRepository.save(leaveRequest);
        return LeaveResponse.from(leaveRequest);
    }

    private <T extends Enum<T>> T parseEnum(String rawValue, Class<T> enumType) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        try {
            return Enum.valueOf(enumType, rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
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
