package com.yanus.attendance.attendance.application;

import com.yanus.attendance.attendance.domain.Attendance;
import com.yanus.attendance.attendance.domain.AttendanceRepository;
import com.yanus.attendance.attendance.presentation.dto.AttendanceResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        validateNotAlreadyCheckedIn(memberId);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.now());
        attendanceRepository.save(attendance);
        return AttendanceResponse.from(attendance);
    }

    public AttendanceResponse checkOut(Long memberId) {
        Attendance attendance = findTodayAttendance(memberId);
        attendance.checkOut(LocalDateTime.now());
        return AttendanceResponse.from(attendance);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> getMyAttendances(Long memberId) {
        return attendanceRepository.findAllByMemberId(memberId)
                .stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateNotAlreadyCheckedIn(Long memberId) {
        attendanceRepository.findByMemberIdAndWorkDate(memberId, LocalDate.now())
                .ifPresent(attendance -> {
                    throw new BusinessException(ErrorCode.ALREADY_CHECKED_IN);
                });
    }

    private Attendance findTodayAttendance(Long memberId) {
        return attendanceRepository.findByMemberIdAndWorkDate(memberId, LocalDate.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHECKED_IN));
    }
}
