package com.yanus.attendance.calendar.application;

import com.yanus.attendance.calendar.domain.CalendarEvent;
import com.yanus.attendance.calendar.domain.CalendarEventRepository;
import com.yanus.attendance.calendar.presentation.dto.CalendarEventCreateRequest;
import com.yanus.attendance.calendar.presentation.dto.CalendarEventResponse;
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
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final MemberRepository memberRepository;

    public CalendarEventResponse create(Long memberId, CalendarEventCreateRequest request) {
        Member member = findMember(memberId);
        CalendarEvent event = CalendarEvent.create(member, request.title(),
                request.startDate(), request.startTime(),
                request.endDate(), request.endTime());
        calendarEventRepository.save(event);
        return CalendarEventResponse.from(event);
    }

    public CalendarEventResponse update(Long eventId, CalendarEventCreateRequest request) {
        CalendarEvent event = findEvent(eventId);
        event.update(request.title(),
                request.startDate(), request.startTime(),
                request.endDate(), request.endTime());
        return CalendarEventResponse.from(event);
    }

    public void delete(Long eventId) {
        calendarEventRepository.deleteById(eventId);
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getByDateRange(LocalDate startDate, LocalDate endDate) {
        return calendarEventRepository.findByDateRange(startDate, endDate).stream()
                .map(CalendarEventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getByCreatedBy(Long memberId) {
        return calendarEventRepository.findByCreatedBy(memberId).stream()
                .map(CalendarEventResponse::from)
                .toList();
    }

    private CalendarEvent findEvent(Long id) {
        return calendarEventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
