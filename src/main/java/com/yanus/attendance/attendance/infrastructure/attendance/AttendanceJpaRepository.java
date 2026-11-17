package com.yanus.attendance.attendance.infrastructure.attendance;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceRepository;
import com.yanus.attendance.attendance.domain.attendance.AttendanceStatus;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceJpaRepository implements AttendanceRepository {

    private final AttendanceSpringDataRepository repository;
    private final JdbcTemplate jdbc;
    private final TimeZone jdbcTimeZone;

    public AttendanceJpaRepository(
            AttendanceSpringDataRepository repository,
            JdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.jdbc.time_zone:}") String jdbcTimeZone
    ) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.jdbcTimeZone = jdbcTimeZone.isBlank()
                ? TimeZone.getDefault()
                : TimeZone.getTimeZone(ZoneId.of(jdbcTimeZone));
    }

    @Override
    public Attendance save(Attendance attendance) {
        return repository.save(attendance);
    }

    @Override
    public void insertIfAbsent(Attendance attendance) {
        jdbc.update("""
                INSERT INTO attendance (member_id, work_date, check_in_time, status)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (member_id, work_date) DO NOTHING
                """, statement -> {
            statement.setLong(1, attendance.getMember().getId());
            statement.setObject(2, attendance.getWorkDate());
            // Match Hibernate's timestamp binding so JPA reads both new and existing rows consistently.
            statement.setTimestamp(3, Timestamp.valueOf(attendance.getCheckInTime()), Calendar.getInstance(jdbcTimeZone));
            statement.setString(4, attendance.getStatus().name());
        });
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

    @Override
    public List<Attendance> findAllByWorkDateAndStatus(LocalDate workDate, AttendanceStatus status) {
        return repository.findAllByWorkDateAndStatus(workDate, status);
    }

    @Override
    public List<Attendance> findByMemberIdAndWorkDateBetween(Long memberId, LocalDate start, LocalDate end) {
        return repository.findByMemberIdAndWorkDateBetween(memberId, start, end);
    }

    @Override
    public void delete(Attendance attendance) {
        repository.delete(attendance);
    }
}
