package com.yanus.attendance.attendance.application.exception;

import com.yanus.attendance.attendance.application.setting.AttendanceSettingService;
import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceRepository;
import com.yanus.attendance.attendance.domain.attendance.AttendanceStatus;
import com.yanus.attendance.attendance.domain.exception.*;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleRepository;
import com.yanus.attendance.attendance.presentation.dto.exception.AttendanceExceptionListResponse;
import com.yanus.attendance.attendance.presentation.dto.exception.AttendanceExceptionResponse;
import com.yanus.attendance.attendance.presentation.dto.exception.AttendanceExceptionSummary;
import com.yanus.attendance.attendance.presentation.dto.exception.BulkAutoCheckoutResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
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
public class AttendanceExceptionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AttendanceExceptionRepository exceptionRepository;
    private final AttendanceRepository attendanceRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final MemberRepository memberRepository;
    private final AttendanceSettingService settingService;
    private final AttendanceExceptionJudge judge = new AttendanceExceptionJudge();

    @Transactional
    public List<AttendanceException> getExceptions(
            LocalDate date,
            AttendanceExceptionType type,
            AttendanceExceptionStatus status,
            String teamName) {
        generateMissingExceptions(date);
        return filterExceptions(exceptionRepository.findAllByWorkDate(date), type, status, teamName);
    }

    @Transactional(readOnly = true)
    public AttendanceExceptionSummary getSummary(LocalDate date) {
        List<AttendanceException> all = exceptionRepository.findAllByWorkDate(date);
        return buildSummary(all, all);
    }

    @Transactional
    public AttendanceExceptionListResponse getList(
            LocalDate date,
            AttendanceExceptionType type,
            AttendanceExceptionStatus status,
            String teamName) {
        generateMissingExceptions(date);
        List<AttendanceException> all = exceptionRepository.findAllByWorkDate(date);
        List<AttendanceException> filtered = filterExceptions(all, type, status, teamName);
        AttendanceExceptionSummary summary = buildSummary(all, filtered);
        List<AttendanceExceptionResponse> items = filtered.stream()
                .map(this::toResponse)
                .toList();
        return new AttendanceExceptionListResponse(date, summary, items);
    }

    @Transactional
    public AttendanceExceptionResponse approve(Long id, String approvedBy, String note) {
        AttendanceException exception = findException(id);
        exception.approve(approvedBy, LocalDateTime.now(), note);
        return toResponse(exception);
    }

    @Transactional
    public AttendanceExceptionResponse reject(Long id, String note) {
        AttendanceException exception = findException(id);
        exception.reject(note);
        return toResponse(exception);
    }

    @Transactional
    public AttendanceExceptionResponse resolve(Long id, String resolvedBy, String note) {
        AttendanceException exception = findException(id);
        exception.resolve(resolvedBy, LocalDateTime.now(), note);
        return toResponse(exception);
    }

    @Transactional
    public AttendanceExceptionResponse updateNote(Long id, String note, String reason) {
        AttendanceException exception = findException(id);
        exception.updateNote(note, reason);
        return toResponse(exception);
    }

    @Transactional
    public BulkAutoCheckoutResponse bulkAutoCheckout(LocalDate date, List<Long> memberIds, String actor) {
        generateMissingExceptions(date);
        List<AttendanceException> targets = exceptionRepository
                .findAllByWorkDateAndType(date, AttendanceExceptionType.MISSED_CHECK_OUT)
                .stream()
                .filter(e -> includesMember(e, memberIds))
                .filter(e -> e.getStatus() != AttendanceExceptionStatus.RESOLVED)
                .filter(e -> e.getAttendance() != null)
                .toList();
        List<Long> updatedIds = targets.stream()
                .map(e -> autoCheckOut(e, actor))
                .toList();
        return new BulkAutoCheckoutResponse(updatedIds.size(), updatedIds);
    }

    private AttendanceException findException(Long id) {
        return exceptionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_EXCEPTION_NOT_FOUND));
    }

    private AttendanceExceptionResponse toResponse(AttendanceException exception) {
        WorkSchedule schedule = findSchedule(exception.getMember(), exception.getWorkDate());
        return AttendanceExceptionResponse.from(exception, startOf(schedule), endOf(schedule));
    }

    private LocalTime startOf(WorkSchedule schedule) {
        if (schedule == null) {
            return null;
        }
        return schedule.getStartTime();
    }

    private LocalTime endOf(WorkSchedule schedule) {
        if (schedule == null) {
            return null;
        }
        return schedule.getEndTime();
    }

    private AttendanceExceptionSummary buildSummary(
            List<AttendanceException> all, List<AttendanceException> filtered) {
        return new AttendanceExceptionSummary(
                all.size(),
                filtered.size(),
                countByStatus(all, AttendanceExceptionStatus.OPEN),
                countByType(all, AttendanceExceptionType.MISSED_CHECK_IN),
                countByType(all, AttendanceExceptionType.MISSED_CHECK_OUT),
                countByType(all, AttendanceExceptionType.LATE),
                countByType(all, AttendanceExceptionType.NO_SCHEDULE)
        );
    }

    private void generateMissingExceptions(LocalDate date) {
        memberRepository.findAll()
                .forEach(member -> generateFor(member, date));
    }

    private void generateFor(Member member, LocalDate date) {
        WorkSchedule schedule = findSchedule(member, date);
        Attendance attendance = findAttendance(member, date);
        boolean thresholdPassed = isThresholdPassed(date, schedule);
        List<AttendanceExceptionType> detected = judge.judge(schedule, attendance, thresholdPassed);
        detected.forEach(type -> saveIfAbsent(member, attendance, date, type));
    }

    private WorkSchedule findSchedule(Member member, LocalDate date) {
        return workScheduleRepository
                .findByMemberIdAndDayOfWeek(member.getId(), date.getDayOfWeek())
                .orElse(null);
    }

    private Attendance findAttendance(Member member, LocalDate date) {
        return attendanceRepository
                .findByMemberIdAndWorkDate(member.getId(), date)
                .orElse(null);
    }

    private void saveIfAbsent(Member member, Attendance attendance,
                              LocalDate date, AttendanceExceptionType type) {
        if (alreadyExists(member, date, type)) {
            return;
        }
        exceptionRepository.save(AttendanceException.open(member, attendance, date, type));
    }

    private boolean alreadyExists(Member member, LocalDate date, AttendanceExceptionType type) {
        return exceptionRepository
                .findByMemberIdAndWorkDateAndType(member.getId(), date, type)
                .isPresent();
    }

    private boolean isThresholdPassed(LocalDate date, WorkSchedule schedule) {
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime deadline = deadlineOf(date, schedule);
        return !now.isBefore(deadline);
    }

    private LocalDateTime deadlineOf(LocalDate date, WorkSchedule schedule) {
        if (schedule == null) {
            return date.atTime(settingService.getAutoCheckoutTimeValue());
        }
        if (schedule.isEndsNextDay()) {
            return date.plusDays(1).atTime(schedule.getEndTime());
        }
        return date.atTime(schedule.getEndTime());
    }

    private List<AttendanceException> filterExceptions(
            List<AttendanceException> all,
            AttendanceExceptionType type,
            AttendanceExceptionStatus status,
            String teamName) {
        return all.stream()
                .filter(e -> matchesType(e, type))
                .filter(e -> matchesStatus(e, status))
                .filter(e -> matchesTeam(e, teamName))
                .toList();
    }

    private boolean matchesType(AttendanceException e, AttendanceExceptionType type) {
        if (type == null) {
            return true;
        }
        return e.getType() == type;
    }

    private boolean matchesStatus(AttendanceException e, AttendanceExceptionStatus status) {
        if (status == null) {
            return true;
        }
        return e.getStatus() == status;
    }

    private boolean matchesTeam(AttendanceException e, String teamName) {
        if (teamName == null) {
            return true;
        }
        return teamName.equals(e.getMember().getTeam().getName());
    }

    private int countByType(List<AttendanceException> exceptions, AttendanceExceptionType type) {
        return (int) exceptions.stream()
                .filter(e -> e.getType() == type)
                .count();
    }

    private int countByStatus(List<AttendanceException> exceptions, AttendanceExceptionStatus status) {
        return (int) exceptions.stream()
                .filter(e -> e.getStatus() == status)
                .count();
    }

    private boolean includesMember(AttendanceException exception, List<Long> memberIds) {
        if (memberIds == null) {
            return true;
        }
        return memberIds.contains(exception.getMember().getId());
    }

    private Long autoCheckOut(AttendanceException exception, String actor) {
        Attendance attendance = exception.getAttendance();
        WorkSchedule schedule = findSchedule(exception.getMember(), exception.getWorkDate());
        closeAttendance(attendance, exception.getWorkDate(), schedule);
        exception.resolve(actor, LocalDateTime.now(KST), "자동 퇴근 처리");
        return attendance.getId();
    }

    private void closeAttendance(Attendance attendance, LocalDate workDate, WorkSchedule schedule) {
        if (attendance.getStatus() != AttendanceStatus.WORKING) {
            return;
        }
        attendance.checkOut(resolveCheckOutTime(attendance, workDate, schedule));
    }

    private LocalDateTime resolveCheckOutTime(Attendance attendance, LocalDate workDate, WorkSchedule schedule) {
        if (isOvernight(schedule)) {
            return workDate.plusDays(1).atTime(schedule.getEndTime());
        }
        return resolveDaytimeCheckOut(attendance, workDate);
    }

    private boolean isOvernight(WorkSchedule schedule) {
        if (schedule == null) {
            return false;
        }
        return schedule.isEndsNextDay();
    }

    private LocalDateTime resolveDaytimeCheckOut(Attendance attendance, LocalDate workDate) {
        LocalTime configured = settingService.getAutoCheckoutTimeValue();
        LocalDateTime candidate = workDate.atTime(configured);
        if (candidate.isAfter(attendance.getCheckInTime())) {
            return candidate;
        }
        return workDate.atTime(23, 59, 59);
    }
}