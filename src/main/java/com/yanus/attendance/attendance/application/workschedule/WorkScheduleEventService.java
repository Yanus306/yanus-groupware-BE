package com.yanus.attendance.attendance.application.workschedule;

import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEvent;
import com.yanus.attendance.attendance.domain.workschedule.WorkScheduleEventRepository;
import com.yanus.attendance.attendance.presentation.dto.workschedule.WorkScheduleEventRequest;
import com.yanus.attendance.attendance.presentation.dto.workschedule.WorkScheduleEventResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkScheduleEventService {

    private final WorkScheduleEventRepository workScheduleEventRepository;
    private final MemberRepository memberRepository;

    public WorkScheduleEventResponse createEvent(Long memberId, WorkScheduleEventRequest request) {
        Member member = findMember(memberId);
        validateNoDuplicate(memberId, request.date());
        WorkScheduleEvent event = WorkScheduleEvent.create(member, request.date(), request.startTime(), request.endTime());
        workScheduleEventRepository.save(event);
        return WorkScheduleEventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public List<WorkScheduleEventResponse> getEvents(Long memberId, LocalDate startDate, LocalDate endDate) {
        return workScheduleEventRepository.findAllByMemberIdAndDateBetween(memberId, startDate, endDate)
                .stream().map(WorkScheduleEventResponse::from).toList();
    }

    public WorkScheduleEventResponse updateEvent(Long memberId, Long eventId, WorkScheduleEventRequest request) {
        WorkScheduleEvent event = findEventAndValidateOwner(memberId, eventId);
        event.update(request.startTime(), request.endTime());
        return WorkScheduleEventResponse.from(event);
    }

    public void deleteEvent(Long memberId, Long eventId) {
        WorkScheduleEvent event = findEventAndValidateOwner(memberId, eventId);
        workScheduleEventRepository.delete(event);
    }

    private void validateNoDuplicate(Long memberId, LocalDate date) {
        workScheduleEventRepository.findByMemberIdAndDate(memberId, date)
                .ifPresent(e -> { throw new BusinessException(ErrorCode.WORK_SCHEDULE_EVENT_DUPLICATE); });
    }

    private WorkScheduleEvent findEventAndValidateOwner(Long memberId, Long eventId) {
        WorkScheduleEvent event = workScheduleEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_SCHEDULE_NOT_FOUND));
        if (!event.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return event;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
