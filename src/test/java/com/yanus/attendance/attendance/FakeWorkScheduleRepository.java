package com.yanus.attendance.attendance;

import com.yanus.attendance.attendance.domain.WorkSchedule;
import com.yanus.attendance.attendance.domain.WorkScheduleRepository;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeWorkScheduleRepository implements WorkScheduleRepository {

    private final Map<Long, WorkSchedule> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public WorkSchedule save(WorkSchedule workSchedule) {
        if (workSchedule.getId() == null) {
            ReflectionTestUtils.setField(workSchedule, "id", sequence++);
        }
        store.put(workSchedule.getId(), workSchedule);
        return workSchedule;
    }

    @Override
    public Optional<WorkSchedule> findByMemberIdAndDayOfWeek(Long memberId, DayOfWeek dayOfWeek) {
        return store.values().stream()
                .filter(s -> s.getMember().getId().equals(memberId))
                .filter(s -> s.getDayOfWeek() == dayOfWeek)
                .findFirst();
    }

    @Override
    public List<WorkSchedule> findAllByMemberId(Long memberId) {
        return store.values().stream()
                .filter(s -> s.getMember().getId().equals(memberId))
                .toList();
    }
}
