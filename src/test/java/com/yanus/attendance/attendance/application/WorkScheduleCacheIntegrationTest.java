package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import com.yanus.attendance.AttendanceApplication;
import com.yanus.attendance.attendance.application.dto.workschedule.MemberWorkScheduleResponse;
import com.yanus.attendance.attendance.application.workschedule.AdminWorkScheduleCache;
import com.yanus.attendance.attendance.application.workschedule.WorkScheduleService;
import com.yanus.attendance.auth.infrastructure.JwtTokenProvider;
import com.yanus.attendance.member.domain.MemberRole;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = AttendanceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.driver-class-name=org.postgresql.Driver", "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
                "yanus.work-schedule-cache.enabled=true", "management.server.port=0",
                "spring.data.redis.connect-timeout=100ms", "spring.data.redis.timeout=100ms"})
class WorkScheduleCacheIntegrationTest {

    private static final String PATH = "/api/v1/work-schedules";
    private static final String KEY_PREFIX = "yanus:work-schedules:all:v1:";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.15");
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.0.15").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;
    @MockitoSpyBean StringRedisTemplate redis;
    @Autowired HikariDataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AdminWorkScheduleCache cache;
    @Autowired WorkScheduleService schedules;
    @LocalServerPort int port;

    long team;
    long admin;
    long member;

    @BeforeEach
    void fixture() {
        jdbc.execute("truncate member, team restart identity cascade");
        team = jdbc.queryForObject("insert into team(name,created_at) values ('Alpha', now()) returning team_id", Long.class);
        admin = insertMember("Admin", MemberRole.ADMIN);
        member = insertMember("Member", MemberRole.MEMBER);
        jdbc.update("""
                insert into work_schedule(member_id,day_of_week,start_time,end_time,week_pattern,ends_next_day)
                values (?,'MONDAY','09:00','18:00','EVERY',false)
                """, member);
    }

    @Test
    void appliesPermissionAndReadsCurrentScheduleAndNamesAfterCommit() throws Exception {
        assertThat(get().path("memberName").asText()).isEqualTo("Member");
        String oldKey = key();
        assertThat(redis.hasKey(oldKey)).isTrue();
        assertThat(send(member, MemberRole.MEMBER, "GET", "/all", null).statusCode()).isEqualTo(403);
        assertThat(send(member, MemberRole.MEMBER, "PUT", "", """
                {"dayOfWeek":"MONDAY","startTime":"10:00","endTime":"18:00","weekPattern":"EVERY"}
                """).statusCode()).isEqualTo(200);
        assertThat(key()).isNotEqualTo(oldKey);
        assertThat(start(get())).isEqualTo("10:00:00");

        String beforeRollback = key();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("update work_schedule set start_time = '08:00' where member_id = ?", member);
            assertThat(key()).isNotEqualTo(beforeRollback);
            tx.setRollbackOnly();
        });
        assertThat(key()).isEqualTo(beforeRollback);
        assertThat(start(get())).isEqualTo("10:00:00");

        long otherTeam = jdbc.queryForObject(
                "insert into team(name,created_at) values ('Beta', now()) returning team_id", Long.class);
        jdbc.update("update member set name='Renamed',team_id=? where member_id=?", otherTeam, member);
        JsonNode moved = get();
        assertThat(moved.path("memberName").asText()).isEqualTo("Renamed");
        assertThat(moved.path("teamName").asText()).isEqualTo("Beta");
        jdbc.update("update team set name='Renamed team' where team_id=?", otherTeam);
        assertThat(get().path("teamName").asText()).isEqualTo("Renamed team");

        assertThat(send(member, MemberRole.MEMBER, "DELETE", "/MONDAY", null).statusCode()).isEqualTo(200);
        assertThat(get().isMissingNode()).isTrue();
        jdbc.update("update member set role='MEMBER' where member_id=?", admin);
        assertThat(send(admin, MemberRole.ADMIN, "GET", "/all", null).statusCode()).isEqualTo(403);
    }

    @Test
    void releasesPostgresConnectionBeforeReadingOrWritingRedis() {
        redis.delete(key());
        List<Integer> activeConnections = new ArrayList<>();
        doAnswer(invocation -> {
            activeConnections.add(dataSource.getHikariPoolMXBean().getActiveConnections());
            return invocation.callRealMethod();
        }).when(redis).opsForValue();

        var cacheMiss = schedules.getAllWorkSchedules(admin);
        assertThat(cacheMiss).hasSize(1);
        assertThat(schedules.getAllWorkSchedules(admin)).isEqualTo(cacheMiss);
        assertThat(activeConnections).containsExactly(0, 0, 0);
    }

    @Test
    void lateCacheFillCanOnlyWriteTheVersionItReadBeforeCommit() throws Exception {
        List<MemberWorkScheduleResponse> oldPayload = schedules.getAllWorkSchedules(admin);
        String oldKey = key();
        redis.delete(oldKey);
        CountDownLatch oldLoadStarted = new CountDownLatch(1);
        CountDownLatch allowOldFill = new CountDownLatch(1);
        var oldRead = CompletableFuture.supplyAsync(() -> cache.getForAdmin(admin, () -> {
            oldLoadStarted.countDown();
            try {
                if (!allowOldFill.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Concurrent commit did not complete");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
            return oldPayload;
        }));
        try {
            assertThat(oldLoadStarted.await(5, TimeUnit.SECONDS)).isTrue();
            jdbc.update("update work_schedule set start_time = '10:30' where member_id = ?", member);
            assertThat(key()).isNotEqualTo(oldKey);
        } finally {
            allowOldFill.countDown();
        }
        assertThat(oldRead.get(5, TimeUnit.SECONDS)).isEqualTo(oldPayload);
        assertThat(redis.opsForValue().get(oldKey)).contains("09:00:00");
        assertThat(start(get())).isEqualTo("10:30:00");
    }

    @Test
    void readsPostgresDuringRedisStallAndSkipsOldEntriesAfterRecovery() throws Exception {
        assertThat(start(get())).isEqualTo("09:00:00");
        String keyBeforeStall = key();
        assertThat(redis.hasKey(keyBeforeStall)).isTrue();
        var docker = REDIS.getDockerClient();
        docker.pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            jdbc.update("update work_schedule set start_time = '11:00' where member_id = ?", member);
            long started = System.nanoTime();
            assertThat(start(get())).isEqualTo("11:00:00");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        } finally {
            docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(start(get())).isEqualTo("11:00:00");
            assertThat(redis.hasKey(key())).isTrue();
        });
        assertThat(redis.opsForValue().get(keyBeforeStall)).contains("09:00:00");
    }

    private JsonNode get() throws Exception {
        var response = send(admin, MemberRole.ADMIN, "GET", "/all", null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("data").path(0);
    }

    private static String start(JsonNode member) {
        return member.path("workSchedules").path(0).path("startTime").asText();
    }

    private String key() {
        return KEY_PREFIX + jdbc.queryForObject("select revision::text from work_schedule_cache_revision", String.class);
    }

    private long insertMember(String name, MemberRole role) {
        return jdbc.queryForObject("""
                insert into member(name,email,password,role,status,team_id,created_at)
                values (?,?,'fixture',?,'ACTIVE',?,now()) returning member_id
                """, Long.class, name, name + "@example.invalid", role.name(), team);
    }

    private HttpResponse<String> send(long actor, MemberRole role, String method, String route, String body)
            throws Exception {
        var payload = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + PATH + route))
                .method(method, payload).timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + jwt.createAccessToken(actor, role)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
