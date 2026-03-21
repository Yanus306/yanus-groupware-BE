package com.yanus.attendance.attendance.infrastructure;

import com.yanus.attendance.attendance.domain.Attendance;
import com.yanus.attendance.attendance.domain.AttendanceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AttendanceJpaRepository implements AttendanceRepository {

    private final AttendanceSpringDataRepository repository;

    @Override
    public Attendance save(Attendance attendance) {
        return repository.save(attendance);
    }

    @Override
    public Optional<Attendance> findByMemberIdAndWorkDate(Long memberId, LocalDate workDate) {
        return repository.findByMemberIdAndWorkDate(memberId, workDate);
    }

    @Override
    public List<Attendance> findAllByMemberId(Long memberId) {
        return repository.findAllByMemberId(memberId);
    }

    @Override
    public List<Attendance> findAllByWorkDate(LocalDate workDate) {
        return repository.findAllByWorkDate(workDate);
    }
}
