package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanus.attendance.AttendanceApplication;
import com.yanus.attendance.attendance.application.exception.AttendanceExceptionService;
import com.yanus.attendance.auth.infrastructure.JwtTokenProvider;
import com.yanus.attendance.member.domain.MemberRole;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = AttendanceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.driver-class-name=org.postgresql.Driver", "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
                "spring.jpa.properties.hibernate.format_sql=false", "management.server.port=0"})
class AttendanceExceptionQueryIntegrationTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.15")
            .withCommand("postgres", "-c", "shared_preload_libraries=pg_stat_statements");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired AttendanceExceptionService service;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;
    @Autowired PlatformTransactionManager transactionManager;
    @LocalServerPort int port;

    @BeforeEach
    void resetFixture() {
        jdbc.execute("create extension if not exists pg_stat_statements");
        jdbc.execute("truncate member, team restart identity cascade");
    }

    @Test
    void readsTheSameNumberOfStatementsForTenAndOneHundredMembers() {
        long team = team("Alpha");
        var selects = new ArrayList<Long>();
        int existing = 0;
        for (int size : List.of(10, 100)) {
            for (int i = existing; i < size; i++) {
                schedule(member(team, "member-" + i));
            }
            existing = size;
            LocalDate date = size == 10 ? MONDAY : MONDAY.plusWeeks(1);
            jdbc.execute("select pg_stat_statements_reset()");
            assertThat(service.getList(date, null, null, null).items()).hasSize(size);
            selects.add(statementCalls("select"));
            assertThat(exceptionCount(date)).isEqualTo(size);
            jdbc.execute("select pg_stat_statements_reset()");
            assertThat(service.getList(date, null, null, null).items()).hasSize(size);
            selects.add(statementCalls("select"));
            assertThat(statementCalls("insert")).isZero();
            assertThat(exceptionCount(date)).isEqualTo(size);
        }

        assertThat(selects).as("SELECTs for first/repeat at 10 and 100 members")
                .allSatisfy(reads -> assertThat(reads).isBetween(1L, 7L))
                .allMatch(selects.getFirst()::equals);
    }

    @Test
    void filtersOnlyTheListAndKeepsTheEntireDaySummary() throws Exception {
        long alpha = team("Alpha");
        long bravo = team("Bravo");
        long first = member(alpha, "first");
        long approved = member(alpha, "approved");
        long rejected = member(bravo, "rejected");
        long unscheduled = member(bravo, "unscheduled");
        List.of(first, approved, rejected).forEach(this::schedule);
        long approvedId = storedException(approved, "APPROVED", "keep approval");
        long rejectedId = storedException(rejected, "REJECTED", "keep rejection");
        long attendanceId = attendance(unscheduled, "09:00");

        var filtered = read(send(first, MONDAY, "&teamName=Alpha&type=missed_check_in&status=open"));
        assertThat(filtered.path("summary")).isEqualTo(json.createObjectNode()
                .put("totalCount", 4).put("filteredCount", 1).put("openCount", 2)
                .put("missedCheckInCount", 3).put("missedCheckOutCount", 0)
                .put("lateCount", 0).put("noScheduleCount", 1));
        assertThat(filtered.path("items").size()).isEqualTo(1);
        var item = filtered.path("items").get(0);
        assertThat(item.path("memberId").asLong()).isEqualTo(first);
        assertThat(item.path("scheduledStartAt").asText()).isEqualTo("2026-10-05T09:00:00");
        assertThat(item.path("attendanceRecordId").isNull()).isTrue();

        var noSchedule = read(send(first, MONDAY, "&type=NO_SCHEDULE"));
        assertThat(noSchedule.path("items").get(0).path("attendanceRecordId").asLong()).isEqualTo(attendanceId);
        assertThat(read(send(first, MONDAY, "&status=APPROVED")).path("items").get(0).path("id").asLong())
                .isEqualTo(approvedId);
        assertThat(read(send(first, MONDAY, "&status=REJECTED")).path("items").get(0).path("id").asLong())
                .isEqualTo(rejectedId);
        assertThat(jdbc.queryForList("""
                select note from attendance_exception where id in (?,?) order by id
                """, String.class, approvedId, rejectedId)).containsExactly("keep approval", "keep rejection");
    }

    @Test
    void rejectsAnInvalidFilterBeforeCreatingExceptions() throws Exception {
        long member = member(team("Alpha"), "member");
        schedule(member);
        assertThat(send(member, MONDAY, "&type=UNKNOWN").statusCode()).isEqualTo(400);
        assertThat(send(member, MONDAY, "&status=UNKNOWN").statusCode()).isEqualTo(400);
        assertThat(exceptionCount(MONDAY)).isZero();
    }

    @Test
    void concurrentFirstReadsReturnTheSameRowsAndPreserveExistingDecisions() throws Exception {
        long team = team("Alpha");
        long first = member(team, "first");
        long second = member(team, "second");
        long approved = member(team, "approved");
        long rejected = member(team, "rejected");
        List.of(first, second, approved, rejected).forEach(this::schedule);
        storedException(approved, "APPROVED", "keep approval");
        storedException(rejected, "REJECTED", "keep rejection");

        var request = request(first, MONDAY, "");
        CompletableFuture<HttpResponse<String>> a;
        CompletableFuture<HttpResponse<String>> b;
        try (var gate = dataSource.getConnection()) {
            gate.setAutoCommit(false);
            int blockerPid;
            try (var statement = gate.createStatement()) {
                statement.execute("lock table attendance_exception in share mode");
                try (var result = statement.executeQuery("select pg_backend_pid()")) {
                    assertThat(result.next()).isTrue();
                    blockerPid = result.getInt(1);
                }
            }
            try {
                a = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                b = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                awaitBlockedInserts(blockerPid);
            } finally {
                gate.rollback();
            }
        }
        var firstResponse = a.get(25, TimeUnit.SECONDS);
        var secondResponse = b.get(25, TimeUnit.SECONDS);
        assertThat(List.of(firstResponse.statusCode(), secondResponse.statusCode()))
                .as("concurrent HTTP statuses").containsExactly(200, 200);
        JsonNode firstBody = read(firstResponse);
        JsonNode secondBody = read(secondResponse);
        assertThat(firstBody.path("summary")).isEqualTo(secondBody.path("summary"));
        assertThat(firstBody.path("items")).containsExactlyInAnyOrderElementsOf(secondBody.path("items"));
        assertThat(exceptionCount(MONDAY)).isEqualTo(4);
        assertThat(jdbc.queryForList("""
                select status || ':' || note from attendance_exception where member_id in (?,?) order by member_id
                """, String.class, approved, rejected)).containsExactly("APPROVED:keep approval", "REJECTED:keep rejection");
    }

    @Test
    void autoCheckoutSeesGeneratedRowsAndResolvesOnlyTheSelectedMember() {
        long team = team("Alpha");
        long selected = member(team, "selected");
        long other = member(team, "other");
        List.of(selected, other).forEach(this::schedule);
        long selectedAttendance = attendance(selected, "09:00");
        attendance(other, "09:00");

        assertThat(service.bulkAutoCheckout(MONDAY, List.of(selected), "tester").updatedIds())
                .containsExactly(selectedAttendance);
        assertThat(jdbc.queryForList("""
                select status from attendance_exception where work_date=? order by member_id
                """, String.class, MONDAY)).containsExactly("RESOLVED", "OPEN");
        assertThat(jdbc.queryForList("select status from attendance order by member_id", String.class))
                .containsExactly("LEFT", "WORKING");
    }

    @Test
    void rollingBackTheReadTransactionAlsoRollsBackTheGeneratedRows() {
        long member = member(team("Alpha"), "member");
        schedule(member);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(service.getList(MONDAY, null, null, null).items()).hasSize(1);
            status.setRollbackOnly();
        });

        assertThat(exceptionCount(MONDAY)).isZero();
    }

    private void awaitBlockedInserts(int blockerPid) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        int blocked;
        do {
            blocked = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where ? = any(pg_blocking_pids(pid))
                      and regexp_replace(lower(query), '[[:space:]]+', ' ', 'g')
                          like '%insert into attendance_exception (%'
                    """, Integer.class, blockerPid);
            if (blocked == 2) return;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        assertThat(blocked).as("INSERTs waiting for the same table lock").isEqualTo(2);
    }

    private long statementCalls(String verb) {
        return jdbc.queryForObject("""
                select coalesce(sum(calls), 0) from pg_stat_statements
                where lower(ltrim(query)) like ? and query not ilike '%pg_stat_statements%'
                """, Long.class, verb + " %");
    }

    private HttpResponse<String> send(long memberId, LocalDate date, String filters) throws Exception {
        return HTTP.send(request(memberId, date, filters), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(long memberId, LocalDate date, String filters) {
        return HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/v1/attendance-exceptions?date=" + date + filters))
                .header("Authorization", "Bearer " + jwt.createAccessToken(memberId, MemberRole.MEMBER))
                .timeout(Duration.ofSeconds(30)).GET().build();
    }

    private JsonNode read(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as("response %s", response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private long team(String name) {
        return jdbc.queryForObject("insert into team(name,created_at) values (?,now()) returning team_id",
                Long.class, name);
    }

    private long member(long team, String name) {
        return jdbc.queryForObject("""
                insert into member(name,email,password,role,status,team_id,created_at)
                values (?,?,'fixture','MEMBER','ACTIVE',?,now()) returning member_id
                """, Long.class, name, name + "@example.invalid", team);
    }

    private void schedule(long member) {
        jdbc.update("""
                insert into work_schedule(member_id,day_of_week,start_time,end_time,week_pattern,ends_next_day)
                values (?,'MONDAY','09:00','18:00','EVERY',false)
                """, member);
    }

    private long storedException(long member, String status, String note) {
        return jdbc.queryForObject("""
                insert into attendance_exception(member_id,work_date,type,status,note)
                values (?,?,'MISSED_CHECK_IN',?,?) returning id
                """, Long.class, member, MONDAY, status, note);
    }

    private long attendance(long member, String time) {
        return jdbc.queryForObject("""
                insert into attendance(member_id,work_date,check_in_time,status)
                values (?,?,cast(? as timestamp),'WORKING') returning attendance_id
                """, Long.class, member, MONDAY, MONDAY + " " + time);
    }

    private long exceptionCount(LocalDate date) {
        return jdbc.queryForObject("select count(*) from attendance_exception where work_date=?", Long.class, date);
    }
}
