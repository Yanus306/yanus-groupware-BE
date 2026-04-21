package com.yanus.attendance.attendance.application.attendance;

import com.yanus.attendance.attendance.application.setting.AttendanceSettingService;
import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceQueryRepository;
import com.yanus.attendance.attendance.domain.attendance.AttendanceRepository;
import com.yanus.attendance.attendance.domain.attendance.AttendanceStatus;
import com.yanus.attendance.attendance.presentation.dto.attendance.AttendanceRangeResponse;
import com.yanus.attendance.attendance.presentation.dto.attendance.AttendanceResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AttendanceRepository attendanceRepository;
    private final MemberRepository memberRepository;
    private final AttendanceQueryRepository attendanceQueryRepository;
    private final AttendanceSettingService attendanceSettingService;

    public AttendanceResponse checkIn(Long memberId) {
        Member member = findMember(memberId);
        validateNotAlreadyCheckedIn(memberId);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.now(KST));
        attendanceRepository.save(attendance);
        return AttendanceResponse.from(attendance);
    }

    public AttendanceResponse checkOut(Long memberId) {
        Attendance attendance = findWorkingAttendance(memberId);
        attendance.checkOut(LocalDateTime.now(KST));
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

    public void autoCheckOut(LocalDate workDate, LocalTime currentTime) {
        LocalTime configuredTime = attendanceSettingService.getAutoCheckoutTimeValue();
        if (currentTime.isBefore(configuredTime)) {
            return;
        }
        List<Attendance> workingAttendances =
                attendanceRepository.findAllByWorkDateAndStatus(workDate, AttendanceStatus.WORKING);
        LocalDateTime checkOutTime = workDate.atTime(configuredTime);
        workingAttendances.forEach(attendance -> attendance.checkOut(checkOutTime));
    }

    public AttendanceResponse checkIn(Long memberId, String clientIp) {
        validateAttendanceIp(clientIp);
        Member member = findMember(memberId);
        validateNotAlreadyCheckedIn(memberId);
        Attendance attendance = Attendance.checkIn(member, LocalDateTime.now(KST));
        attendanceRepository.save(attendance);
        return AttendanceResponse.from(attendance);
    }

    public void resetAttendance(Long memberId, LocalDate today) {
        Attendance attendance = attendanceRepository.findByMemberIdAndWorkDate(memberId, today)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND));
        attendanceRepository.delete(attendance);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRangeResponse> getAttendancesByRange(Long requesterId, Long targetMemberId, LocalDate startDate, LocalDate endDate) {
        Member target = resolveTarget(requesterId, targetMemberId);
        return attendanceRepository.findByMemberIdAndWorkDateBetween(target.getId(), startDate, endDate)
                .stream()
                .map(AttendanceRangeResponse::from)
                .toList();
    }

    private Member resolveTarget(Long requesterId, Long targetMemberId) {
        Member requester = findMember(requesterId);
        if (requester.getRole() == MemberRole.ADMIN) {
            return memberRepository.findById(targetMemberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        }
        if (requester.getRole() == MemberRole.TEAM_LEAD) {
            Member target = memberRepository.findById(targetMemberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            validateSameTeam(requester, target);
            return target;
        }
        if (!requesterId.equals(targetMemberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return requester;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Attendance findWorkingAttendance(Long memberId) {
        LocalDate today = LocalDate.now(KST);
        Attendance todays = findWorkingOn(memberId, today);
        if (todays != null) {
            return todays;
        }
        Attendance yesterdays = findWorkingOn(memberId, today.minusDays(1));
        if (yesterdays != null) {
            return yesterdays;
        }
        throw new BusinessException(ErrorCode.NOT_CHECKED_IN);
    }

    private Attendance findWorkingOn(Long memberId, LocalDate date) {
        return attendanceRepository.findByMemberIdAndWorkDate(memberId, date)
                .filter(attendance -> attendance.getStatus() == AttendanceStatus.WORKING)
                .orElse(null);
    }

    private void validateAttendanceIp(String clientIp) {
        if (!clientIp.startsWith("220.69")) {
            throw new BusinessException(ErrorCode.INVALID_ATTENDANCE_IP);
        }
    }

    private void validateNotAlreadyCheckedIn(Long memberId) {
        attendanceRepository.findByMemberIdAndWorkDate(memberId, LocalDate.now(KST))
                .ifPresent(attendance -> {
                    throw new BusinessException(ErrorCode.ALREADY_CHECKED_IN);
                });
    }

    private void validateSameTeam(Member requester, Member target) {
        if (!requester.getTeam().getName().equals(target.getTeam().getName())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
