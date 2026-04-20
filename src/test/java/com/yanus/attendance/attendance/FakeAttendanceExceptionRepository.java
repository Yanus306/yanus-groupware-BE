package com.yanus.attendance.attendance;

import com.yanus.attendance.attendance.domain.exception.AttendanceException;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionRepository;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionType;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeAttendanceExceptionRepository implements AttendanceExceptionRepository {

    private final Map<Long, AttendanceException> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public AttendanceException save(AttendanceException exception) {
        if (exception.getId() == null) {
            ReflectionTestUtils.setField(exception, "id", sequence++);
        }
        store.put(exception.getId(), exception);
        return exception;
    }

    @Override
    public Optional<AttendanceException> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<AttendanceException> findByMemberIdAndWorkDateAndType(
            Long memberId, LocalDate workDate, AttendanceExceptionType type) {
        return store.values().stream()
                .filter(e -> e.getMember().getId().equals(memberId))
                .filter(e -> e.getWorkDate().equals(workDate))
                .filter(e -> e.getType() == type)
                .findFirst();
    }

    @Override
    public List<AttendanceException> findAllByWorkDate(LocalDate workDate) {
        return store.values().stream()
                .filter(e -> e.getWorkDate().equals(workDate))
                .toList();
    }

    @Override
    public List<AttendanceException> findAllByWorkDateAndType(LocalDate workDate, AttendanceExceptionType type) {
        return store.values().stream()
                .filter(e -> e.getWorkDate().equals(workDate))
                .filter(e -> e.getType() == type)
                .toList();
    }
}