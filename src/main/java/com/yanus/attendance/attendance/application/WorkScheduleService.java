package com.yanus.attendance.attendance.application;

import com.yanus.attendance.attendance.domain.WorkSchedule;
import com.yanus.attendance.attendance.domain.WorkScheduleRepository;
import com.yanus.attendance.attendance.presentation.dto.MemberWorkScheduleResponse;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleRequest;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkScheduleService {

    private final WorkScheduleRepository workScheduleRepository;
    private final MemberRepository memberRepository;

    public WorkScheduleResponse setWorkSchedule(Long memberId, WorkScheduleRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Optional<WorkSchedule> existing = workScheduleRepository.findByMemberIdAndDayOfWeek(memberId, request.dayOfWeek());

        if (existing.isPresent()) {
            existing.get().update(request.startTime(), request.endTime());
            return WorkScheduleResponse.from(existing.get());
        }

        WorkSchedule schedule = WorkSchedule.create(member, request.dayOfWeek(), request.startTime(), request.endTime());
        workScheduleRepository.save(schedule);

        return WorkScheduleResponse.from(schedule);
    }

    @Transactional(readOnly = true)
    public List<WorkScheduleResponse> getMyWorkSchedules(Long memberId) {
        return workScheduleRepository.findAllByMemberId(memberId).stream()
                .map(WorkScheduleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemberWorkScheduleResponse> getTeamWorkSchedules(Long teamId) {
        return groupByMember(workScheduleRepository.findAllByMemberTeamId(teamId));
    }

    @Transactional(readOnly = true)
    public List<MemberWorkScheduleResponse> getAllWorkSchedules() {
        return groupByMember(workScheduleRepository.findAll());
    }

    private List<MemberWorkScheduleResponse> groupByMember(List<WorkSchedule> schedules) {
        return schedules.stream()
                .collect(Collectors.groupingBy(WorkSchedule::getMember))
                .entrySet().stream()
                .map(entry -> new MemberWorkScheduleResponse(
                        entry.getKey().getId(),
                        entry.getKey().getName(),
                        entry.getKey().getTeam().getName(),
                        entry.getValue().stream().map(WorkScheduleResponse::from).toList()
                ))
                .toList();
    }

    public void deleteWorkSchedule(Long memberId, DayOfWeek dayOfWeek) {
        workScheduleRepository.findByMemberIdAndDayOfWeek(memberId, dayOfWeek)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_SCHEDULE_NOT_FOUND));
        workScheduleRepository.deleteByMemberIdAndDayOfWeek(memberId, dayOfWeek);
    }
}
