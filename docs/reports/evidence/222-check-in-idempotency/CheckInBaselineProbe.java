import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanus.attendance.AttendanceApplication;
import com.yanus.attendance.auth.infrastructure.JwtTokenProvider;
import com.yanus.attendance.member.domain.MemberRole;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

public class CheckInBaselineProbe {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    private static final String ALLOWED_IP = "220.69.1.1";
    private static final String OUTSIDE_IP = "203.0.113.7";

    public static void main(String[] args) throws Exception {
        boolean expectIdempotent = Boolean.parseBoolean(System.getenv("YANUS_222_EXPECT_IDEMPOTENT"));
        var result = new LinkedHashMap<String, Object>();
        result.put("commit", System.getenv("GITHUB_SHA"));
        result.put("source", expectIdempotent ? "local implementation based on commit" : "baseline commit");
        result.put("java_version", System.getProperty("java.version"));
        result.put("endpoint", "POST /api/v1/attendances/check-in");
        try (var postgres = new PostgreSQLContainer<>("postgres:16.15")) {
            postgres.start();
            try (var app = new SpringApplicationBuilder(AttendanceApplication.class).run(
                    "--server.address=127.0.0.1", "--server.port=0", "--management.server.port=0",
                    "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.username=" + postgres.getUsername(),
                    "--spring.datasource.password=" + postgres.getPassword(),
                    "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true",
                    "--spring.jpa.properties.hibernate.format_sql=false",
                    "--jwt.secret=local-222-probe-only-secret-at-least-32-chars",
                    "--minio.endpoint=http://127.0.0.1:1", "--minio.access-key=probe", "--minio.secret-key=probe-secret",
                    "--spring.mail.host=127.0.0.1", "--spring.mail.port=1", "--spring.mail.username=probe",
                    "--spring.mail.password=probe", "--logging.level.root=WARN",
                    "--logging.level.com.yanus.attendance=ERROR")) {
                int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
                URI endpoint = URI.create("http://127.0.0.1:" + port + "/api/v1/attendances/check-in");
                var db = app.getBean(JdbcTemplate.class);
                var jwt = app.getBean(JwtTokenProvider.class);
                result.put("postgresql_version", db.queryForObject("show server_version", String.class));
                result.put("isolation", db.queryForObject("show transaction_isolation", String.class));
                result.put("flyway_latest", db.queryForObject(
                        "select version from flyway_schema_history where success order by installed_rank desc limit 1",
                        String.class));
                long team = db.queryForObject(
                        "insert into team(name,created_at) values ('CheckInProbe',now()) returning team_id", Long.class);
                long sequentialMember = member(db, team, "sequential");
                String sequentialToken = jwt.createAccessToken(sequentialMember, MemberRole.MEMBER);
                var first = describe(HTTP.send(request(endpoint, sequentialToken, ALLOWED_IP), HttpResponse.BodyHandlers.ofString()));
                var retry = describe(HTTP.send(request(endpoint, sequentialToken, ALLOWED_IP), HttpResponse.BodyHandlers.ofString()));
                var missingJwt = describe(HTTP.send(request(endpoint, null, ALLOWED_IP), HttpResponse.BodyHandlers.ofString()));
                var outsideIp = describe(HTTP.send(request(endpoint, sequentialToken, OUTSIDE_IP), HttpResponse.BodyHandlers.ofString()));
                var sequentialRows = rows(db, sequentialMember);
                result.put("sequential", Map.of("first", first, "retry", retry, "rows", sequentialRows));
                result.put("security_controls", Map.of("no_jwt", missingJwt, "outside_ip_after_check_in", outsideIp));
                var races = new ArrayList<Map<String, Object>>();
                for (int round = 1; round <= 3; round++) {
                    long member = member(db, team, "race-" + round);
                    String token = jwt.createAccessToken(member, MemberRole.MEMBER);
                    races.add(runRace(postgres, db, endpoint, token, member, round, expectIdempotent));
                }
                result.put("races", races);
                JSON.writerWithDefaultPrettyPrinter().writeValue(
                        Path.of(System.getenv("YANUS_222_PROBE_DIR"), "results.json").toFile(), result);
                System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
                require(status(first) == 200 && sequentialRows.size() == 1, "First request did not persist one row");
                if (expectIdempotent) {
                    require(first.equals(retry) && first.get("id").equals(sequentialRows.getFirst().get("attendance_id")),
                            "Retry did not return the first persisted check-in");
                } else {
                    require(status(retry) == 400 && retry.get("code").equals("ALREADY_CHECKED_IN"),
                            "Sequential baseline signature differed");
                }
                require(status(missingJwt) == 403 && status(outsideIp) == 403
                        && outsideIp.get("code").equals("INVALID_ATTENDANCE_IP"), "Security control signature differed");
                for (var race : races) {
                    var expectedStatuses = expectIdempotent ? List.of(200, 200) : List.of(200, 500);
                    require(race.get("sorted_statuses").equals(expectedStatuses)
                            && race.get("persisted_rows").equals(1), "Concurrency signature differed");
                }
            }
        }
    }

    private static Map<String, Object> runRace(PostgreSQLContainer<?> postgres, JdbcTemplate db,
                                               URI endpoint, String token, long member, int round,
                                               boolean expectIdempotent) throws Exception {
        try (var gate = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            gate.setAutoCommit(false);
            int gatePid;
            try (var statement = gate.createStatement()) {
                try (var id = statement.executeQuery("select pg_backend_pid()")) {
                    id.next();
                    gatePid = id.getInt(1);
                }
                statement.execute("lock table attendance in share mode");
            }
            var a = HTTP.sendAsync(request(endpoint, token, ALLOWED_IP), HttpResponse.BodyHandlers.ofString());
            var b = HTTP.sendAsync(request(endpoint, token, ALLOWED_IP), HttpResponse.BodyHandlers.ofString());
            List<Map<String, Object>> waiting;
            try {
                waiting = awaitTwoInserts(db, gatePid);
            } finally {
                gate.rollback();
            }
            var resultA = describe(a.get(10, TimeUnit.SECONDS));
            var resultB = describe(b.get(10, TimeUnit.SECONDS));
            var stored = rows(db, member);
            if (expectIdempotent) {
                require(stored.size() == 1 && resultA.equals(resultB)
                                && resultA.get("id").equals(stored.getFirst().get("attendance_id")),
                        "Concurrent responses did not return the same persisted check-in");
            }
            var status = List.of(status(resultA), status(resultB)).stream().sorted().toList();
            return Map.of("round", round, "blocked_inserts", waiting, "response_a", resultA, "response_b", resultB,
                    "sorted_statuses", status, "persisted_rows", stored.size(), "rows", stored);
        }
    }

    private static List<Map<String, Object>> awaitTwoInserts(JdbcTemplate db, int gatePid) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        List<Map<String, Object>> waiting;
        do {
            waiting = db.queryForList("""
                    select pid, wait_event_type, wait_event, query
                    from pg_stat_activity
                    where ? = any(pg_blocking_pids(pid))
                      and regexp_replace(lower(query), '[[:space:]]+', ' ', 'g') like '%insert into attendance (%'
                    order by pid
                    """, gatePid);
            if (waiting.size() == 2) {
                return waiting;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("Expected two blocked attendance INSERTs, observed " + waiting);
    }

    private static long member(JdbcTemplate db, long team, String name) {
        return db.queryForObject("""
                insert into member(name,email,password,role,status,team_id,created_at)
                values (?, ?, 'local-fixture', 'MEMBER', 'ACTIVE', ?, now()) returning member_id
                """, Long.class, name, name + "@example.invalid", team);
    }

    private static List<Map<String, Object>> rows(JdbcTemplate db, long member) {
        return db.queryForList("""
                select attendance_id, member_id, work_date::text, check_in_time::text, status
                from attendance where member_id=? order by attendance_id
                """, member);
    }

    private static HttpRequest request(URI endpoint, String token, String ip) {
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(18))
                .header("X-Forwarded-For", ip).POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request.build();
    }

    private static Map<String, Object> describe(HttpResponse<String> response) throws Exception {
        var output = new LinkedHashMap<String, Object>();
        output.put("http", response.statusCode());
        var body = JSON.readTree(response.body());
        if (body != null && body.has("code")) {
            output.put("code", body.path("code").asText());
        }
        if (body != null && body.path("data").isObject()) {
            var data = body.path("data");
            output.put("id", data.path("id").asLong());
            output.put("work_date", data.path("workDate").asText());
            output.put("check_in_time", data.path("checkInTime").asText());
            output.put("status", data.path("status").asText());
        }
        return output;
    }

    private static int status(Map<String, Object> response) {
        return (Integer) response.get("http");
    }

    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalStateException(reason);
    }
}
