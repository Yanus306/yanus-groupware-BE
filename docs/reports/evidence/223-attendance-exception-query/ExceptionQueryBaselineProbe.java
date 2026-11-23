import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanus.attendance.AttendanceApplication;
import com.yanus.attendance.attendance.application.exception.AttendanceExceptionService;
import com.yanus.attendance.auth.infrastructure.JwtTokenProvider;
import com.yanus.attendance.member.domain.MemberRole;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

public class ExceptionQueryBaselineProbe {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private static final int HTTP_WARMUPS = 20;
    private static final int HTTP_SAMPLES = 100;

    public static void main(String[] args) throws Exception {
        var result = new LinkedHashMap<String, Object>();
        result.put("baseline_commit", System.getenv().getOrDefault("YANUS_223_BASELINE_COMMIT", System.getenv("GITHUB_SHA")));
        result.put("source_tree_head", System.getenv("GITHUB_SHA"));
        result.put("variant", System.getenv().getOrDefault("YANUS_223_VARIANT", "baseline"));
        result.put("production_source_fingerprint", System.getenv().getOrDefault("YANUS_223_SOURCE_FINGERPRINT", ""));
        result.put("java_version", System.getProperty("java.version"));
        try (var postgres = new PostgreSQLContainer<>("postgres:16.15")
                .withCommand("postgres", "-c", "shared_preload_libraries=pg_stat_statements")) {
            postgres.start();
            try (var context = new SpringApplicationBuilder(AttendanceApplication.class).run(
                    "--server.address=127.0.0.1", "--server.port=0", "--management.server.port=0",
                    "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.username=" + postgres.getUsername(),
                    "--spring.datasource.password=" + postgres.getPassword(),
                    "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true",
                    "--spring.jpa.properties.hibernate.generate_statistics=true",
                    "--spring.jpa.properties.hibernate.session.events.log=false",
                    "--spring.jpa.properties.hibernate.format_sql=false",
                    "--jwt.secret=local-223-probe-only-secret-at-least-32-chars",
                    "--minio.endpoint=http://127.0.0.1:1", "--minio.access-key=probe", "--minio.secret-key=probe-secret",
                    "--spring.mail.host=127.0.0.1", "--spring.mail.port=1", "--spring.mail.username=probe",
                    "--spring.mail.password=probe",
                    "--logging.level.root=WARN", "--logging.level.com.yanus.attendance=ERROR")) {
                var db = context.getBean(JdbcTemplate.class);
                var statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
                var service = context.getBean(AttendanceExceptionService.class);
                db.execute("create extension pg_stat_statements");
                result.put("database_version", db.queryForObject("show server_version", String.class));
                result.put("isolation", db.queryForObject("show transaction_isolation", String.class));
                result.put("flyway_latest", db.queryForObject(
                        "select version from flyway_schema_history where success order by installed_rank desc limit 1",
                        String.class));
                long teamId = db.queryForObject(
                        "insert into team(name,created_at) values ('Probe',now()) returning team_id", Long.class);
                var samples = new ArrayList<Map<String, Object>>();
                int existing = 0;
                for (int size : List.of(10, 100)) {
                    seed(db, teamId, existing, size);
                    existing = size;
                    LocalDate date = size == 10 ? LocalDate.of(2026, 10, 5) : LocalDate.of(2026, 10, 12);
                    require(exceptionCount(db, date) == 0, "first lookup must start without a persisted exception");
                    samples.add(Map.of("members", size, "date", date.toString(),
                            "first", measure(db, statistics, service, date),
                            "repeat", measure(db, statistics, service, date)));
                }
                result.put("statement_counts", samples);

                int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
                var jwt = context.getBean(JwtTokenProvider.class);
                long member = db.queryForObject("select min(member_id) from member", Long.class);
                String token = jwt.createAccessToken(member, MemberRole.MEMBER);
                result.put("concurrent_first_http", race(postgres, db, port, token, LocalDate.of(2026, 10, 19)));
                if (Boolean.parseBoolean(System.getenv("YANUS_223_HTTP_BENCHMARK"))) {
                    statistics.setStatisticsEnabled(false);
                    LocalDate date = LocalDate.of(2026, 10, 26);
                    var request = listRequest(port, token, date);
                    result.put("http_benchmark", Map.of(
                            "members", 100, "date", date.toString(), "concurrency", 1,
                            "warmups_per_scenario", HTTP_WARMUPS, "samples_per_scenario", HTTP_SAMPLES,
                            "first", latency(db, request, date, true),
                            "repeat", latency(db, request, date, false)));
                }
            }
        }
        JSON.writerWithDefaultPrettyPrinter().writeValue(
                Path.of(System.getenv("YANUS_223_PROBE_DIR"), "baseline.json").toFile(), result);
        System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    private static Map<String, Object> measure(JdbcTemplate db, Statistics statistics,
                                               AttendanceExceptionService service, LocalDate date) {
        db.execute("select pg_stat_statements_reset()");
        statistics.clear();
        var response = service.getList(date, null, null, null);
        long prepared = statistics.getPrepareStatementCount();
        var statements = db.queryForList("""
                select
                  lower(split_part(ltrim(query), ' ', 1)) as verb,
                  regexp_replace(query, '[[:space:]]+', ' ', 'g') as sql,
                  calls,
                  rows
                from pg_stat_statements
                where ltrim(query) ~* '^(select|insert|update|delete)[[:space:]]'
                  and query not ilike '%pg_stat_statements%'
                order by calls desc, sql
                """);
        var totals = new LinkedHashMap<String, Long>();
        for (var statement : statements) {
            totals.merge((String) statement.get("verb"), ((Number) statement.get("calls")).longValue(), Long::sum);
        }
        return Map.of("hibernate_prepared_statements", prepared, "postgres_calls_by_verb", totals,
                "postgres_statements", statements, "items", response.items().size(),
                "persisted_exceptions", exceptionCount(db, date));
    }

    private static Map<String, Object> race(PostgreSQLContainer<?> postgres, JdbcTemplate db, int port,
                                          String token, LocalDate date) throws Exception {
        require(exceptionCount(db, date) == 0, "race must start without persisted exceptions");
        try (var blocker = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            blocker.setAutoCommit(false);
            int blockerPid;
            try (var statement = blocker.createStatement()) {
                statement.execute("lock table attendance_exception in share mode");
                try (var result = statement.executeQuery("select pg_backend_pid()")) {
                    require(result.next(), "missing blocker backend PID");
                    blockerPid = result.getInt(1);
                }
            }
            var request = listRequest(port, token, date);
            CompletableFuture<HttpResponse<String>> first;
            CompletableFuture<HttpResponse<String>> second;
            List<Map<String, Object>> blocked;
            try {
                first = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                second = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                blocked = awaitInserts(db, blockerPid);
            } finally {
                blocker.rollback();
            }
            var a = first.get(25, TimeUnit.SECONDS);
            var b = second.get(25, TimeUnit.SECONDS);
            var bodyA = JSON.readTree(a.body());
            var bodyB = JSON.readTree(b.body());
            boolean sameSummary = a.statusCode() == 200 && b.statusCode() == 200
                    && bodyA.path("summary").equals(bodyB.path("summary"));
            boolean sameItems = sameSummary && itemsById(bodyA).equals(itemsById(bodyB));
            return Map.of("date", date.toString(), "blocked_inserts", blocked,
                    "responses", List.of(describe(a), describe(b)), "same_summary", sameSummary,
                    "same_items", sameItems, "persisted_exceptions", exceptionCount(db, date));
        }
    }

    private static Map<Long, JsonNode> itemsById(JsonNode body) {
        var items = new LinkedHashMap<Long, JsonNode>();
        body.path("items").forEach(item -> items.put(item.path("id").asLong(), item));
        return items;
    }

    private static Map<String, Object> latency(JdbcTemplate db, HttpRequest request, LocalDate date,
                                               boolean first) throws Exception {
        db.update("delete from attendance_exception where work_date = ?", date);
        if (!first) {
            require(HTTP.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200, "seed repeat");
            require(exceptionCount(db, date) == 100, "repeat measurement must have 100 rows ahead of time");
        }
        var samples = new ArrayList<Double>();
        var statuses = new TreeMap<Integer, Integer>();
        int errors = 0;
        for (int i = -HTTP_WARMUPS; i < HTTP_SAMPLES; i++) {
            if (first) {
                db.update("delete from attendance_exception where work_date = ?", date);
            }
            long start = System.nanoTime();
            HttpResponse<String> response = null;
            try {
                response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException ex) {
                System.err.println("Benchmark transport error: " + ex.getClass().getSimpleName());
            }
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            int code = response == null ? 0 : response.statusCode();
            boolean valid = code == 200 && JSON.readTree(response.body()).path("items").size() == 100;
            if (i >= 0) {
                statuses.merge(code, 1, Integer::sum);
                if (valid) {
                    samples.add(elapsedMs);
                } else {
                    errors++;
                }
            } else {
                require(valid, "warm-up returned status " + code + " or a wrong item count");
            }
        }
        require(!samples.isEmpty(), "no successful HTTP samples");
        List<Double> ordered = samples.stream().sorted().toList();
        return Map.of("p50_ms", percentile(ordered, 0.50), "p95_ms", percentile(ordered, 0.95),
                "http_status_counts", statuses, "errors", errors, "error_rate", (double) errors / HTTP_SAMPLES,
                "successful_response_samples_ms", samples);
    }

    private static double percentile(List<Double> ordered, double ratio) {
        return ordered.get((int) Math.ceil(ratio * ordered.size()) - 1);
    }

    private static HttpRequest listRequest(int port, String token, LocalDate date) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/attendance-exceptions?date=" + date))
                .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(30)).GET().build();
    }

    private static List<Map<String, Object>> awaitInserts(JdbcTemplate db, int blockerPid) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        List<Map<String, Object>> blocked;
        do {
            blocked = db.queryForList("""
                    select pid, wait_event_type, wait_event
                    from pg_stat_activity
                    where ? = any(pg_blocking_pids(pid))
                      and regexp_replace(lower(query), '[[:space:]]+', ' ', 'g')
                          like '%insert into attendance_exception (%'
                    order by pid
                    """, blockerPid);
            if (blocked.size() == 2) {
                return blocked;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("expected two concurrent INSERTs, observed " + blocked);
    }

    private static Map<String, Object> describe(HttpResponse<String> response) throws Exception {
        var body = JSON.readTree(response.body());
        return Map.of("http_status", response.statusCode(), "code", body.path("code").asText(""),
                "items", body.path("items").size());
    }

    private static void seed(JdbcTemplate db, long teamId, int start, int end) {
        for (int i = start; i < end; i++) {
            long id = db.queryForObject("""
                    insert into member(name,email,password,role,status,team_id,created_at)
                    values (?,?,'fixture','MEMBER','ACTIVE',?,now()) returning member_id
                    """, Long.class, "probe-" + i, "probe-" + i + "@example.invalid", teamId);
            db.update("""
                    insert into work_schedule(member_id,day_of_week,start_time,end_time,week_pattern,ends_next_day)
                    values (?,'MONDAY','09:00','18:00','EVERY',false)
                    """, id);
        }
    }

    private static int exceptionCount(JdbcTemplate db, LocalDate date) {
        return db.queryForObject("select count(*) from attendance_exception where work_date=?", Integer.class, date);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
