package dev.sentinel.checking;

import com.sun.net.httpserver.HttpServer;
import dev.sentinel.monitoring.MonitorSnapshot;
import org.junit.jupiter.api.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class HttpCheckerTest {
    private HttpServer server;
    private HttpChecker checker;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final CountDownLatch release = new CountDownLatch(1);
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> { exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server.createContext("/error", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.createContext("/redirect", exchange -> { exchange.getResponseHeaders().add("Location", "/ok"); exchange.sendResponseHeaders(302, -1); exchange.close(); });
        server.createContext("/slow", exchange -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.createContext("/stream", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().flush();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
    }
    private HttpChecker checker(long elapsedMs) {
        AtomicLong ticker = new AtomicLong();
        checker = new HttpChecker(new DestinationPolicy(Set.of("127.0.0.1"), InetAddress::getAllByName),
                Clock.fixed(now, ZoneOffset.UTC), () -> ticker.getAndAdd(elapsedMs * 1_000_000));
        return checker;
    }
    private MonitorSnapshot monitor(String path, int threshold) {
        return new MonitorSnapshot(UUID.randomUUID(), "http://127.0.0.1:" + server.getAddress().getPort() + path, 1000, threshold, 0);
    }
    @AfterEach void cleanup() throws Exception {
        release.countDown();
        server.stop(0);
        if (checker != null) checker.close();
    }
    @Test void successUsesMonotonicElapsedAndWallTimestamp() {
        var result = checker(25).check(monitor("/ok", 25));
        assertThat(result.status()).isEqualTo(HealthStatus.UP);
        assertThat(result.httpStatus()).isEqualTo(204);
        assertThat(result.durationMs()).isEqualTo(25);
        assertThat(result.checkedAt()).isEqualTo(now);
        assertThat(result.failureReason()).isNull();
    }
    @Test void doesNotWaitForResponseBody() {
        var result = checker(10).check(monitor("/stream", 25));
        assertThat(result.status()).isEqualTo(HealthStatus.UP);
        assertThat(result.httpStatus()).isEqualTo(200);
    }
    @Test void slowSuccessIsDegraded() {
        assertThat(checker(26).check(monitor("/ok", 25)).status()).isEqualTo(HealthStatus.DEGRADED);
    }
    @Test void httpErrorPreservesStatus() {
        var result = checker(10).check(monitor("/error", 25));
        assertThat(result.status()).isEqualTo(HealthStatus.DOWN);
        assertThat(result.httpStatus()).isEqualTo(503);
        assertThat(result.failureReason()).isEqualTo(FailureReason.HTTP_ERROR);
    }
    @Test void redirectsAreNotFollowed() {
        var result = checker(10).check(monitor("/redirect", 25));
        assertThat(result.httpStatus()).isEqualTo(302);
        assertThat(result.status()).isEqualTo(HealthStatus.DOWN);
    }
    @Test void requestTimeoutHasNoHttpStatus() {
        var source = monitor("/slow", 25);
        var result = checker(100).check(new MonitorSnapshot(source.id(), source.url(), 100, 25, 0));
        assertThat(result.failureReason()).isEqualTo(FailureReason.TIMEOUT);
        assertThat(result.httpStatus()).isNull();
    }
    @Test void connectionFailureHasNoHttpStatus() {
        int port = server.getAddress().getPort();
        server.stop(0);
        var result = checker(10).check(new MonitorSnapshot(UUID.randomUUID(), "http://127.0.0.1:" + port, 1000, 25, 0));
        assertThat(result.failureReason()).isEqualTo(FailureReason.CONNECTION_FAILURE);
        assertThat(result.httpStatus()).isNull();
    }
    @Test void dnsFailureHasNoHttpStatus() {
        checker = new HttpChecker(new DestinationPolicy(Set.of(), host -> { throw new UnknownHostException(); }), Clock.systemUTC());
        var result = checker.check(new MonitorSnapshot(UUID.randomUUID(), "https://unresolved.test", 1000, 25, 0));
        assertThat(result.failureReason()).isEqualTo(FailureReason.DNS_FAILURE);
        assertThat(result.httpStatus()).isNull();
    }
    @Test void productionPolicyBlocksLocalEndpoint() {
        checker = new HttpChecker(new DestinationPolicy(Set.of(), InetAddress::getAllByName), Clock.systemUTC());
        assertThat(checker.check(monitor("/ok", 25)).failureReason()).isEqualTo(FailureReason.BLOCKED_DESTINATION);
    }
    @Test void connectionTimeResolverBlocksDnsRebinding() {
        AtomicInteger calls = new AtomicInteger();
        checker = new HttpChecker(new DestinationPolicy(Set.of(), host -> new InetAddress[]{InetAddress.getByName(calls.getAndIncrement() == 0 ? "8.8.8.8" : "127.0.0.1")}), Clock.systemUTC());
        var result = checker.check(new MonitorSnapshot(UUID.randomUUID(), "http://rebound.test:" + server.getAddress().getPort(), 1000, 25, 0));
        assertThat(result.failureReason()).isEqualTo(FailureReason.BLOCKED_DESTINATION);
        assertThat(calls.get()).isEqualTo(2);
    }
}
