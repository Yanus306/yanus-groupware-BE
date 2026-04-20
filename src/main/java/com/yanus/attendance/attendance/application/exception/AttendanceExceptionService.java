package com.yanus.attendance.attendance.application.exception;

import com.yanus.attendance.attendance.application.setting.AttendanceSettingService;
import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceRepository;
import com.yanus.attendance.attendance.domain.exception.*;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleRepository;
import com.yanus.attendance.attendance.presentation.dto.AttendanceExceptionListResponse;
import com.yanus.attendance.attendance.presentation.dto.AttendanceExceptionResponse;
import com.yanus.attendance.attendance.presentation.dto.AttendanceExceptionSummary;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttendanceExceptionService {

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
        boolean thresholdPassed = isThresholdPassed(date);
        memberRepository.findAll()
                .forEach(member -> generateFor(member, date, thresholdPassed));
    }

    private void generateFor(Member member, LocalDate date, boolean thresholdPassed) {
        WorkSchedule schedule = findSchedule(member, date);
        Attendance attendance = findAttendance(member, date);
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

    private boolean isThresholdPassed(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            return true;
        }
        if (date.isAfter(today)) {
            return false;
        }
        LocalTime threshold = settingService.getAutoCheckoutTimeValue();
        return !LocalTime.now().isBefore(threshold);
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
}