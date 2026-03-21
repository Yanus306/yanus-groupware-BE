package com.yanus.attendance.attendance.application;

import com.yanus.attendance.attendance.domain.Attendance;
import com.yanus.attendance.attendance.domain.AttendanceRepository;
import com.yanus.attendance.attendance.presentation.dto.AttendanceResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final MemberRepository memberRepository;

    public AttendanceResponse checkIn(Long memberId) {
        Member member = findMember(memberId);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.now());
        attendanceRepository.save(attendance);
        return AttendanceResponse.from(attendance);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
