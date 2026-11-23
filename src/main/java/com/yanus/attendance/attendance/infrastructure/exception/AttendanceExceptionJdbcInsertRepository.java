package com.yanus.attendance.attendance.infrastructure.exception;

import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.exception.AttendanceException;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionInsertRepository;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceExceptionJdbcInsertRepository implements AttendanceExceptionInsertRepository {

    private static final int BATCH_SIZE = 100;
    private final JdbcTemplate jdbc;
    private final TimeZone jdbcTimeZone;

    public AttendanceExceptionJdbcInsertRepository(
            JdbcTemplate jdbc,
            @Value("${spring.jpa.properties.hibernate.jdbc.time_zone:}") String jdbcTimeZone
    ) {
        this.jdbc = jdbc;
        this.jdbcTimeZone = jdbcTimeZone.isBlank()
                ? TimeZone.getDefault()
                : TimeZone.getTimeZone(ZoneId.of(jdbcTimeZone));
    }

    @Override
    public void insertMissing(List<AttendanceException> candidates) {
        List<AttendanceException> ordered = candidates.stream()
                .sorted(Comparator.comparing((AttendanceException exception) -> exception.getMember().getId())
                        .thenComparing(AttendanceException::getWorkDate)
                        .thenComparing(AttendanceException::getType))
                .toList();
        jdbc.batchUpdate("""
                INSERT INTO attendance_exception (member_id, attendance_id, work_date, type, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (member_id, work_date, type) DO NOTHING
                """, ordered, BATCH_SIZE, (statement, exception) -> {
            Attendance attendance = exception.getAttendance();
            statement.setLong(1, exception.getMember().getId());
            statement.setObject(2, attendance == null ? null : attendance.getId(), Types.BIGINT);
            statement.setObject(3, exception.getWorkDate());
            statement.setString(4, exception.getType().name());
            statement.setString(5, exception.getStatus().name());
            statement.setTimestamp(6, Timestamp.valueOf(exception.getCreatedAt()), Calendar.getInstance(jdbcTimeZone));
        });
    }
}
