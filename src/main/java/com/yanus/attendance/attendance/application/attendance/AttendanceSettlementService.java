package com.yanus.attendance.attendance.application.attendance;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceRepository;
import com.yanus.attendance.attendance.domain.attendance.AttendanceSettlementStatus;
import com.yanus.attendance.attendance.domain.workschedule.WorkSchedule;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEvent;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEventRepository;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleRepository;
import com.yanus.attendance.attendance.presentation.dto.setting.AttendanceSettlementItemResponse;
import com.yanus.attendance.attendance.presentation.dto.setting.AttendanceSettlementResponse;
import com.yanus.attendance.attendance.presentation.dto.setting.ScheduledWindow;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceSettlementService {

    private static final int FEE_PER_MINUTE = 100;

    private final AttendanceRepository attendanceRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final WorkScheduleEventRepository workScheduleEventRepository;
    private final MemberRepository memberRepository;

    public AttendanceSettlementResponse getMonthlySettlement(
            Long requesterId, Long targetMemberId, YearMonth yearMonth) {

        Member requester = findMember(requesterId);
        Member target = resolveTarget(requester, targetMemberId);

        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        Map<LocalDate, WorkScheduleEvent> eventMap = workScheduleEventRepository
                .findAllByMemberIdAndDateBetween(target.getId(), start, end)
                .stream()
                .collect(Collectors.toMap(WorkScheduleEvent::getDate, e -> e));

        List<WorkSchedule> schedules = workScheduleRepository.findAllByMemberId(target.getId());

        Map<LocalDate, Attendance> attendanceMap = attendanceRepository
                .findByMemberIdAndWorkDateBetween(target.getId(), start, end)
                .stream()
                .collect(Collectors.toMap(Attendance::getWorkDate, a -> a));

        List<AttendanceSettlementItemResponse> items = start.datesUntil(end.plusDays(1))
                .map(date -> buildItem(date, schedules, eventMap, attendanceMap))
                .toList();

        return aggregate(target, yearMonth, items);
    }

    private boolean isScheduledDay(LocalDate date,
                                   List<WorkSchedule> schedules, Map<LocalDate, WorkScheduleEvent> eventMap) {
        if (eventMap.containsKey(date)) {
            return true;
        }
        return schedules.stream().anyMatch(s -> s.getDayOfWeek() == date.getDayOfWeek());
    }

    private AttendanceSettlementItemResponse buildItem(
            LocalDate date,
            List<WorkSchedule> schedules,
            Map<LocalDate, WorkScheduleEvent> eventMap,
            Map<LocalDate, Attendance> attendanceMap) {

        ScheduledWindow window = resolveWindow(date, schedules, eventMap);
        if (window == null) {
            return AttendanceSettlementItemResponse.noSchedule(date);
        }

        Attendance attendance = attendanceMap.get(date);
        if (attendance == null) {
            return AttendanceSettlementItemResponse.absent(date, window);
        }

        int lateMinutes = calculateLateMinutes(window.start(), attendance);
        int fee = lateMinutes * FEE_PER_MINUTE;
        AttendanceSettlementStatus status = lateMinutes > 0
                ? AttendanceSettlementStatus.LATE
                : AttendanceSettlementStatus.ON_TIME;

        return AttendanceSettlementItemResponse.of(date, window, attendance, lateMinutes, fee, status);
    }

    private ScheduledWindow resolveWindow(LocalDate date,
                                          List<WorkSchedule> schedules,
                                          Map<LocalDate, WorkScheduleEvent> eventMap) {
        if (eventMap.containsKey(date)) {
            WorkScheduleEvent event = eventMap.get(date);
            return new ScheduledWindow(event.getStartTime(), event.getEndTime(), false); // event 는 당일 가정
        }
        return schedules.stream()
                .filter(s -> s.getDayOfWeek() == date.getDayOfWeek())
                .findFirst()
                .map(s -> new ScheduledWindow(s.getStartTime(), s.getEndTime(), s.isEndsNextDay()))
                .orElse(null);
    }

    private int calculateLateMinutes(LocalTime scheduledStart, Attendance attendance) {
        LocalTime checkIn = attendance.getCheckInTime().toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        LocalTime scheduled = scheduledStart.truncatedTo(ChronoUnit.MINUTES);
        long diff = ChronoUnit.MINUTES.between(scheduled, checkIn);
        return (int) Math.max(0, diff);
    }

    private AttendanceSettlementResponse aggregate(
            Member target, YearMonth yearMonth, List<AttendanceSettlementItemResponse> items) {

        int scheduledDays = (int) items.stream()
                .filter(i -> i.status() != AttendanceSettlementStatus.NO_SCHEDULE)
                .count();
        int attendedDays = (int) items.stream()
                .filter(i -> i.status() != AttendanceSettlementStatus.ABSENT)
                .count();
        int lateDays = (int) items.stream()
                .filter(i -> i.status() == AttendanceSettlementStatus.LATE)
                .count();
        int totalLateMinutes = items.stream().mapToInt(AttendanceSettlementItemResponse::lateMinutes).sum();
        int lateFee = totalLateMinutes * FEE_PER_MINUTE;

        return new AttendanceSettlementResponse(
                yearMonth.toString(),
                target.getId(),
                target.getName(),
                target.getTeam() != null ? target.getTeam().getName() : null,
                scheduledDays, attendedDays, lateDays, totalLateMinutes, lateFee, items);
    }

    private Member resolveTarget(Member requester, Long targetMemberId) {
        if (requester.getRole() == MemberRole.ADMIN) {
            return findMember(targetMemberId);
        }
        if (requester.getRole() == MemberRole.TEAM_LEAD) {
            Member target = findMember(targetMemberId);
            validateSameTeam(requester, target);
            return target;
        }
        if (!requester.getId().equals(targetMemberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return requester;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateSameTeam(Member requester, Member target) {
        if (!requester.getTeam().getName().equals(target.getTeam().getName())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
