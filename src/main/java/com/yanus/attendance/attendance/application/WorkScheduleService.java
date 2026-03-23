package com.yanus.attendance.attendance.application;

import com.yanus.attendance.attendance.domain.WorkSchedule;
import com.yanus.attendance.attendance.domain.WorkScheduleRepository;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleRequest;
import com.yanus.attendance.attendance.presentation.dto.WorkScheduleResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.util.List;
import java.util.Optional;
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
}
