package com.yanus.attendance.attendance;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceRepository;
import com.yanus.attendance.attendance.domain.attendance.AttendanceStatus;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeAttendanceRepository implements AttendanceRepository {

    private final Map<Long, Attendance> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public Attendance save(Attendance attendance) {
        if (attendance.getId() == null) {
            ReflectionTestUtils.setField(attendance, "id", sequence++);
        }
        store.put(attendance.getId(), attendance);
        return attendance;
    }

    @Override
    public Optional<Attendance> findByMemberIdAndWorkDate(Long memberId, LocalDate workDate) {
        return store.values().stream()
                .filter(a -> a.getMember().getId().equals(memberId))
                .filter(a -> a.getWorkDate().equals(workDate))
                .findFirst();
    }

    @Override
    public List<Attendance> findAllByMemberId(Long memberId) {
        return store.values().stream()
                .filter(a -> a.getMember().getId().equals(memberId))
                .toList();
    }

    @Override
    public List<Attendance> findAllByWorkDate(LocalDate workDate) {
        return store.values().stream()
                .filter(a -> a.getWorkDate().equals(workDate))
                .toList();
    }

    @Override
    public List<Attendance> findAllByWorkDateAndStatus(LocalDate workDate, AttendanceStatus status) {
        return store.values().stream()
                .filter(a -> a.getWorkDate().equals(workDate))
                .filter(a -> a.getStatus() == status)
                .toList();
    }

    @Override
    public void delete(Attendance attendance) {
        store.remove(attendance.getId());
    }

    @Override
    public List<Attendance> findByMemberIdAndWorkDateBetween(
            Long memberId, LocalDate startDate, LocalDate endDate) {
        return store.values().stream()
                .filter(a -> a.getMember().getId().equals(memberId))
                .filter(a -> !a.getWorkDate().isBefore(startDate))
                .filter(a -> !a.getWorkDate().isAfter(endDate))
                .toList();
    }
}
