package com.yanus.attendance.attendance.application.exception;

import com.yanus.attendance.attendance.application.setting.AttendanceSettingService;
import com.yanus.attendance.attendance.application.dto.exception.AttendanceExceptionListResponse;
import com.yanus.attendance.attendance.application.dto.exception.AttendanceExceptionResponse;
import com.yanus.attendance.attendance.application.dto.exception.AttendanceExceptionSummary;
import com.yanus.attendance.attendance.application.dto.exception.BulkAutoCheckoutResponse;
import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceStatus;
import com.yanus.attendance.attendance.domain.exception.AttendanceException;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionInput;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionInputRepository;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionInsertRepository;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionJudge;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionKey;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionRepository;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionStatus;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionType;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEvent;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEventRepository;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleRepository;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttendanceExceptionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AttendanceExceptionRepository exceptionRepository;
    private final AttendanceExceptionInsertRepository exceptionInsertRepository;
    private final AttendanceExceptionInputRepository exceptionInputRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final WorkScheduleEventRepository workScheduleEventRepository;
    private final AttendanceSettingService settingService;
    private final AttendanceExceptionJudge judge = new AttendanceExceptionJudge();

    @Transactional
    public List<AttendanceException> getExceptions(
            LocalDate date,
            AttendanceExceptionType type,
            AttendanceExceptionStatus status,
            String teamName) {
        generateMissingExceptions(date);
        return filterExceptions(exceptionRepository.findAllByWorkDateWithDetails(date), type, status, teamName);
    }

    @Transactional(readOnly = true)
    public AttendanceExceptionSummary getSummary(LocalDate date) {
        List<AttendanceException> all = exceptionRepository.findAllByWorkDate(date);
        return buildSummary(all, all);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceExceptionListResponse getList(
            LocalDate date,
            String type,
            String status,
            String teamName) {
        return getList(date,
                parseEnum(type, AttendanceExceptionType.class),
                parseEnum(status, AttendanceExceptionStatus.class),
                teamName
        );
    }

    private AttendanceExceptionListResponse getList(
            LocalDate date,
            AttendanceExceptionType type,
            AttendanceExceptionStatus status,
            String teamName) {
        Map<Long, WorkScheduleWindow> schedules = generateMissingExceptions(date);
        List<AttendanceException> all = exceptionRepository.findAllByWorkDateWithDetails(date);
        List<AttendanceException> filtered = filterExceptions(all, type, status, teamName);
        AttendanceExceptionSummary summary = buildSummary(all, filtered);
        List<AttendanceExceptionResponse> items = filtered.stream()
                .map(exception -> toResponse(exception, schedules.get(exception.getMember().getId())))
                .toList();
        return new AttendanceExceptionListResponse(date, summary, items);
    }

    private <T extends Enum<T>> T parseEnum(String rawValue, Class<T> enumType) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
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
        WorkScheduleWindow schedule = findSchedule(exception.getMember(), exception.getWorkDate());
        return toResponse(exception, schedule);
    }

    private AttendanceExceptionResponse toResponse(AttendanceException exception, WorkScheduleWindow schedule) {
        return AttendanceExceptionResponse.from(
                exception,
                startOf(schedule),
                endOf(schedule),
                endsNextDayOf(schedule)
        );
    }

    private LocalTime startOf(WorkScheduleWindow schedule) {
        if (schedule == null) {
            return null;
        }
        return schedule.startTime();
    }

    private LocalTime endOf(WorkScheduleWindow schedule) {
        if (schedule == null) {
            return null;
        }
        return schedule.endTime();
    }

    private boolean endsNextDayOf(WorkScheduleWindow schedule) {
        if (schedule == null) {
            return false;
        }
        return schedule.endsNextDay();
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

    private Map<Long, WorkScheduleWindow> generateMissingExceptions(LocalDate date) {
        Map<Long, AttendanceExceptionInput> inputsByMember = new HashMap<>();
        Set<AttendanceExceptionKey> existing = new HashSet<>();
        for (AttendanceExceptionInput input : exceptionInputRepository.findGenerationInputs(date, date.getDayOfWeek())) {
            Long memberId = input.member().getId();
            inputsByMember.putIfAbsent(memberId, input);
            if (input.existingType() != null) {
                existing.add(new AttendanceExceptionKey(memberId, input.existingType()));
            }
        }
        Map<Long, WorkScheduleWindow> schedules = new HashMap<>();
        List<AttendanceException> candidates = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(KST);

        for (AttendanceExceptionInput input : inputsByMember.values()) {
            Member member = input.member();
            Long memberId = member.getId();
            WorkScheduleWindow schedule = resolveSchedule(input.scheduleEvent(), input.weeklySchedule());
            if (schedule != null) {
                schedules.put(memberId, schedule);
            }
            Attendance attendance = input.attendance();
            List<AttendanceExceptionType> detected = judge.judgeByScheduledStart(
                    startOf(schedule), attendance, isThresholdPassed(date, schedule, now));
            for (AttendanceExceptionType type : detected) {
                if (existing.add(new AttendanceExceptionKey(memberId, type))) {
                    candidates.add(AttendanceException.open(member, attendance, date, type));
                }
            }
        }
        if (!candidates.isEmpty()) {
            exceptionInsertRepository.insertMissing(candidates);
        }
        return schedules;
    }

    private WorkScheduleWindow findSchedule(Member member, LocalDate date) {
        WorkScheduleEvent event = workScheduleEventRepository
                .findByMemberIdAndDate(member.getId(), date)
                .orElse(null);
        WorkSchedule weekly = event == null
                ? workScheduleRepository.findByMemberIdAndDayOfWeek(member.getId(), date.getDayOfWeek()).orElse(null)
                : null;
        return resolveSchedule(event, weekly);
    }

    private WorkScheduleWindow resolveSchedule(WorkScheduleEvent event, WorkSchedule weekly) {
        if (event != null) {
            return event.isDayOff() ? null : WorkScheduleWindow.from(event);
        }
        return weekly == null ? null : WorkScheduleWindow.from(weekly);
    }

    private boolean isThresholdPassed(LocalDate date, WorkScheduleWindow schedule, LocalDateTime now) {
        if (schedule == null) {
            return false;
        }
        LocalDate endDate = schedule.endsNextDay() ? date.plusDays(1) : date;
        return !now.isBefore(endDate.atTime(schedule.endTime()));
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
        WorkScheduleWindow schedule = findSchedule(exception.getMember(), exception.getWorkDate());
        closeAttendance(attendance, exception.getWorkDate(), schedule);
        exception.resolve(actor, LocalDateTime.now(KST), "자동 퇴근 처리");
        return attendance.getId();
    }

    private void closeAttendance(Attendance attendance, LocalDate workDate, WorkScheduleWindow schedule) {
        if (attendance.getStatus() != AttendanceStatus.WORKING) {
            return;
        }
        attendance.checkOut(resolveCheckOutTime(attendance, workDate, schedule));
    }

    private LocalDateTime resolveCheckOutTime(Attendance attendance, LocalDate workDate, WorkScheduleWindow schedule) {
        if (isOvernight(schedule)) {
            return workDate.plusDays(1).atTime(schedule.endTime());
        }
        return resolveDaytimeCheckOut(attendance, workDate);
    }

    private boolean isOvernight(WorkScheduleWindow schedule) {
        if (schedule == null) {
            return false;
        }
        return schedule.endsNextDay();
    }

    private LocalDateTime resolveDaytimeCheckOut(Attendance attendance, LocalDate workDate) {
        LocalTime configured = settingService.getAutoCheckoutTimeValue();
        LocalDateTime candidate = workDate.atTime(configured);
        if (candidate.isAfter(attendance.getCheckInTime())) {
            return candidate;
        }
        return workDate.atTime(23, 59, 59);
    }

    private record WorkScheduleWindow(
            LocalTime startTime,
            LocalTime endTime,
            boolean endsNextDay
    ) {
        private static WorkScheduleWindow from(WorkSchedule schedule) {
            return new WorkScheduleWindow(
                    schedule.getStartTime(), schedule.getEndTime(), schedule.isEndsNextDay());
        }

        private static WorkScheduleWindow from(WorkScheduleEvent event) {
            return new WorkScheduleWindow(
                    event.getStartTime(), event.getEndTime(), event.isEndsNextDay());
        }
    }
}
