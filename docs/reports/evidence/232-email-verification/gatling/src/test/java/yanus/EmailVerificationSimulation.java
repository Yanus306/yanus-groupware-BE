package yanus;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.Simulation;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class EmailVerificationSimulation extends Simulation {

    public EmailVerificationSimulation() {
        String url = System.getProperty("baseUrl");
        if (!"127.0.0.1".equals(URI.create(url).getHost())) {
            throw new IllegalArgumentException("Use an isolated loopback application");
        }
        String teamId = System.getProperty("teamId");
        String run = System.getProperty("runId");
        int seconds = Integer.getInteger("seconds", 6);
        String accessToken = System.getenv("YANUS_LAB_ACCESS_TOKEN");
        AtomicInteger register = new AtomicInteger();
        AtomicInteger resend = new AtomicInteger();

        var signup = scenario("register")
                .exec(session -> session.set("email", run + "-reg-" + register.incrementAndGet() + "@local.test"))
                .exec(http("register").post("/api/v1/auth/register")
                        .body(StringBody("""
                                {"name":"Lab User","email":"#{email}","password":"lab-pass-123","teamId":%s}
                                """.formatted(teamId))).asJson().check(status().is(200)));

        var retry = scenario("resend")
                .exec(session -> session.set("email", "pending-" + resend.incrementAndGet() + "@local.test"))
                .exec(http("resend").post("/api/v1/auth/verify-email/resend")
                        .body(StringBody("{\"email\":\"#{email}\"}")).asJson().check(status().is(200)));

        var me = scenario("independent-read")
                .exec(http("me").get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken).check(status().is(200)));

        setUp(signup.injectOpen(constantUsersPerSec(2).during(Duration.ofSeconds(seconds))),
                retry.injectOpen(constantUsersPerSec(2).during(Duration.ofSeconds(seconds))),
                me.injectOpen(constantUsersPerSec(6).during(Duration.ofSeconds(seconds))))
                .protocols(http.baseUrl(url));
    }
}
