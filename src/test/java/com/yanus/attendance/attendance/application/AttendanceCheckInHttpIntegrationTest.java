package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanus.attendance.AttendanceApplication;
import com.yanus.attendance.attendance.application.attendance.AttendanceService;
import com.yanus.attendance.attendance.application.dto.attendance.AttendanceResponse;
import com.yanus.attendance.attendance.domain.attendance.Attendance;
import com.yanus.attendance.attendance.domain.attendance.AttendanceRepository;
import com.yanus.attendance.auth.infrastructure.JwtTokenProvider;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = AttendanceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:16.15:///checkin",
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.format_sql=false",
                "spring.jpa.properties.hibernate.jdbc.time_zone=Asia/Seoul",
                "management.server.port=0"
        })
class AttendanceCheckInHttpIntegrationTest {
    private static final String ALLOWED_IP = "220.69.1.1";
    private static final Instant CHECK_IN = Instant.parse("2026-11-17T00:00:00Z");
    private static final TimeZone JDBC_TIME_ZONE = TimeZone.getTimeZone("Asia/Seoul");
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(TIMEOUT)
            .build();

    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider jwt;
    @Autowired ObjectMapper json;
    @Autowired AttendanceService service;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean(name = "attendanceClock") Clock attendanceClock;

    @BeforeEach
    void setCheckInTime() {
        given(attendanceClock.instant()).willReturn(CHECK_IN);
    }

    @Test
    void returnsThePersistedCheckInWhenTheClientRetries() throws Exception {
        long memberId = createMember();
        String token = memberToken(memberId);

        var first = sendCheckIn(token, ALLOWED_IP);
        given(attendanceClock.instant()).willReturn(CHECK_IN.plusSeconds(60));
        var retry = sendCheckIn(token, ALLOWED_IP);

        assertSameCheckIn(memberId, first, retry);
    }

    @Test
    void returnsTheSameCheckInToBothConcurrentRequests() throws Exception {
        long memberId = createMember();
        String token = memberToken(memberId);
        CompletableFuture<HttpResponse<String>> first;
        CompletableFuture<HttpResponse<String>> second;
        try (Connection gate = dataSource.getConnection()) {
            gate.setAutoCommit(false);
            int blockerPid;
            try (var statement = gate.createStatement()) {
                statement.execute("lock table attendance in share mode");
                try (var result = statement.executeQuery("select pg_backend_pid()")) {
                    result.next();
                    blockerPid = result.getInt(1);
                }
            }
            try {
                first = sendCheckInAsync(token);
                second = sendCheckInAsync(token);
                awaitBlockedInserts(blockerPid, 2);
            } finally {
                gate.rollback();
            }
        }

        assertSameCheckIn(memberId, first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void waitingRequestReadsTheCommittedRowOrInsertsAfterRollback(boolean commit) throws Exception {
        long memberId = createMember();
        String token = memberToken(memberId);
        var transaction = new TransactionTemplate(transactions);
        PendingCheckIn pending = transaction.execute(status -> {
            AttendanceResponse first = service.checkIn(memberId, ALLOWED_IP);
            int blockerPid = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
            given(attendanceClock.instant()).willReturn(CHECK_IN.plusSeconds(60));
            var waiting = sendCheckInAsync(token);
            awaitBlockedInserts(blockerPid, 1);
            if (!commit) {
                status.setRollbackOnly();
            }
            return new PendingCheckIn(first, waiting);
        });

        StoredCheckIn second = readCheckIn(pending.response().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        assertThat(pending.first().checkInTime()).isEqualTo(LocalDateTime.parse("2026-11-17T09:00"));
        if (commit) {
            assertThat(second.id()).isEqualTo(pending.first().id());
            assertThat(second.checkInTime()).isEqualTo(pending.first().checkInTime());
        } else {
            assertThat(second.id()).isNotEqualTo(pending.first().id());
            assertThat(second.checkInTime()).isEqualTo(LocalDateTime.parse("2026-11-17T09:01"));
        }
        assertThat(rows(memberId)).containsExactly(second);
    }

    @Test
    void returnsTheCurrentStatusWhenTheMemberHasCheckedOut() throws Exception {
        long memberId = createMember();
        String token = memberToken(memberId);
        LocalDateTime firstCheckIn = LocalDateTime.parse("2026-11-17T08:00");
        LocalDateTime checkOut = firstCheckIn.plusHours(8);
        Long existingId = new TransactionTemplate(transactions).execute(status -> {
            var member = memberRepository.findById(memberId).orElseThrow();
            var attendance = Attendance.checkIn(member, firstCheckIn);
            attendance.checkOut(checkOut);
            return attendanceRepository.save(attendance).getId();
        });

        StoredCheckIn retry = readCheckIn(sendCheckIn(token, ALLOWED_IP));

        assertThat(retry).isEqualTo(new StoredCheckIn(existingId, firstCheckIn.toLocalDate(),
                firstCheckIn, checkOut, "LEFT"));
        assertThat(rows(memberId)).containsExactly(retry);
    }

    @Test
    void usesTheSeoulWorkDateAndKeepsDifferentMembersIndependent() throws Exception {
        long memberId = createMember();
        long otherMemberId = createMember();
        String token = memberToken(memberId);
        given(attendanceClock.instant()).willReturn(Instant.parse("2026-11-17T14:59:59.999999999Z"));
        StoredCheckIn beforeMidnight = readCheckIn(sendCheckIn(token, ALLOWED_IP));
        StoredCheckIn otherMember = readCheckIn(sendCheckIn(memberToken(otherMemberId), ALLOWED_IP));
        given(attendanceClock.instant()).willReturn(Instant.parse("2026-11-17T15:00:00Z"));
        StoredCheckIn afterMidnight = readCheckIn(sendCheckIn(token, ALLOWED_IP));

        assertThat(beforeMidnight.date()).isEqualTo("2026-11-17");
        assertThat(beforeMidnight.checkInTime()).isEqualTo("2026-11-17T23:59:59.999999");
        assertThat(afterMidnight.date()).isEqualTo("2026-11-18");
        assertThat(afterMidnight.checkInTime()).isEqualTo("2026-11-18T00:00:00");
        assertThat(List.of(beforeMidnight.id(), otherMember.id(), afterMidnight.id())).doesNotHaveDuplicates();
        assertThat(otherMember.date()).isEqualTo(beforeMidnight.date());
        assertThat(rows(memberId)).containsExactly(beforeMidnight, afterMidnight);
        assertThat(rows(otherMemberId)).containsExactly(otherMember);
    }

    @Test
    void rejectsInvalidCredentialsAndAnOutsideIpEvenWhenARecordAlreadyExists() throws Exception {
        long memberId = createMember();
        String token = memberToken(memberId);
        StoredCheckIn first = readCheckIn(sendCheckIn(token, ALLOWED_IP));

        assertThat(sendCheckIn(null, ALLOWED_IP).statusCode()).isEqualTo(403);
        assertThat(sendCheckIn("invalid-token", ALLOWED_IP).statusCode()).isEqualTo(403);
        var outside = sendCheckIn(token, "203.0.113.7");
        assertThat(outside.statusCode()).isEqualTo(403);
        assertThat(json.readTree(outside.body()).path("code").asText()).isEqualTo("INVALID_ATTENDANCE_IP");
        var missing = sendCheckIn(memberToken(Long.MAX_VALUE), ALLOWED_IP);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(json.readTree(missing.body()).path("code").asText()).isEqualTo("MEMBER_NOT_FOUND");
        assertThat(rows(memberId)).containsExactly(first);
    }

    private void assertSameCheckIn(long memberId, HttpResponse<String> first, HttpResponse<String> second)
            throws Exception {
        assertThat(List.of(first.statusCode(), second.statusCode()))
                .as("HTTP responses: %s / %s", first.body(), second.body())
                .containsExactly(200, 200);
        StoredCheckIn firstRow = readCheckIn(first);
        assertThat(readCheckIn(second)).isEqualTo(firstRow);
        assertThat(firstRow.status()).isEqualTo("WORKING");
        assertThat(firstRow.checkInTime()).isEqualTo("2026-11-17T09:00:00");
        assertThat(rows(memberId)).containsExactly(firstRow);
    }

    private StoredCheckIn readCheckIn(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as("HTTP body: %s", response.body()).isEqualTo(200);
        JsonNode envelope = json.readTree(response.body());
        assertThat(envelope.path("code").asText()).isEqualTo("SUCCESS");
        JsonNode data = envelope.path("data");
        return new StoredCheckIn(data.path("id").asLong(), LocalDate.parse(data.path("workDate").asText()),
                LocalDateTime.parse(data.path("checkInTime").asText()),
                data.hasNonNull("checkOutTime") ? LocalDateTime.parse(data.path("checkOutTime").asText()) : null,
                data.path("status").asText());
    }

    private void awaitBlockedInserts(int blockerPid, int expected) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        try (Connection observer = dataSource.getConnection();
             var query = observer.prepareStatement("""
                     select count(*)
                       from pg_stat_activity
                      where ? = any(pg_blocking_pids(pid))
                        and regexp_replace(lower(query), '[[:space:]]+', ' ', 'g')
                            like '%insert into attendance (%'
                     """)) {
            query.setInt(1, blockerPid);
            int blocked;
            do {
                try (var result = query.executeQuery()) {
                    result.next();
                    blocked = result.getInt(1);
                }
                if (blocked == expected) {
                    return;
                }
                Thread.sleep(25);
            } while (System.nanoTime() < deadline);
            assertThat(blocked).as("attendance INSERTs waiting on backend %s", blockerPid).isEqualTo(expected);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for PostgreSQL INSERT", ex);
        } catch (SQLException ex) {
            throw new AssertionError("Could not observe PostgreSQL INSERTs", ex);
        }
    }

    private long createMember() {
        String fixture = UUID.randomUUID().toString();
        long teamId = jdbc.queryForObject(
                "insert into team(name, created_at) values (?, now()) returning team_id", Long.class, fixture);
        return jdbc.queryForObject("""
                insert into member(name, email, password, role, status, team_id, created_at)
                values ('check-in test', ?, 'fixture', 'MEMBER', 'ACTIVE', ?, now())
                returning member_id
                """, Long.class, fixture + "@example.invalid", teamId);
    }

    private List<StoredCheckIn> rows(long memberId) {
        return jdbc.query("""
                select attendance_id, work_date, check_in_time, check_out_time, status
                  from attendance where member_id = ? order by work_date
                """, (rs, rowNum) -> new StoredCheckIn(
                rs.getLong("attendance_id"), rs.getObject("work_date", LocalDate.class),
                readTimestamp(rs, "check_in_time"),
                readTimestamp(rs, "check_out_time"), rs.getString("status")), memberId);
    }

    private LocalDateTime readTimestamp(ResultSet row, String column) throws SQLException {
        var timestamp = row.getTimestamp(column, Calendar.getInstance(JDBC_TIME_ZONE));
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String memberToken(long memberId) {
        return jwt.createAccessToken(memberId, MemberRole.MEMBER);
    }

    private HttpResponse<String> sendCheckIn(String token, String ip) throws Exception {
        return HTTP.send(checkInRequest(token, ip), HttpResponse.BodyHandlers.ofString());
    }

    private CompletableFuture<HttpResponse<String>> sendCheckInAsync(String token) {
        return HTTP.sendAsync(checkInRequest(token, ALLOWED_IP), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest checkInRequest(String token, String ip) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/attendances/check-in"))
                .timeout(TIMEOUT.multipliedBy(2))
                .header("X-Forwarded-For", ip)
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request.build();
    }

    private record StoredCheckIn(long id, LocalDate date, LocalDateTime checkInTime,
                                 LocalDateTime checkOutTime, String status) {
    }

    private record PendingCheckIn(AttendanceResponse first, CompletableFuture<HttpResponse<String>> response) {
    }
}
