package com.yanus.attendance.attendance.application;

import com.yanus.attendance.attendance.domain.Attendance;
import com.yanus.attendance.attendance.domain.AttendanceQueryRepository;
import com.yanus.attendance.attendance.domain.AttendanceRepository;
import com.yanus.attendance.attendance.domain.AttendanceStatus;
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
    private final AttendanceQueryRepository attendanceQueryRepository;

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

    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendancesByDate(LocalDate date) {
        return attendanceRepository.findAllByWorkDate(date).stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendancesByFilter(LocalDate date, String teamName) {
        return attendanceQueryRepository.findAllByFilter(date, teamName).stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    public void autoCheckOut(LocalDate workDate) {
        List<Attendance> workingAttendances =
                attendanceRepository.findAllByWorkDateAndStatus(workDate, AttendanceStatus.WORKING);
        LocalDateTime checkOutTime = workDate.atTime(23, 59, 59);
        workingAttendances.forEach(attendance -> attendance.checkOut(checkOutTime));
    }

    public AttendanceResponse checkIn(Long memberId, String clientIp) {
        validateAttendanceIp(clientIp);
        Member member = findMember(memberId);
        validateNotAlreadyCheckedIn(memberId);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.now());
        attendanceRepository.save(attendance);
        return AttendanceResponse.from(attendance);
    }

    public void resetAttendance(Long memberId, LocalDate today) {
        Attendance attendance = attendanceRepository.findByMemberIdAndWorkDate(memberId, today)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND));
        attendanceRepository.delete(attendance);
    }

    private void validateAttendanceIp(String clientIp) {
        if (!clientIp.startsWith("220.69")) {
            throw new BusinessException(ErrorCode.INVALID_ATTENDANCE_IP);
        }
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
