package dev.sentinel.checking;

import dev.sentinel.monitoring.MonitorSnapshot;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;
import javax.net.ssl.SSLException;
import java.io.*;
import java.net.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

@Component
public class HttpChecker {
    private final DestinationPolicy policy;
    private final Clock clock;
    private final LongSupplier ticker;
    private final CloseableHttpClient client;
    private final ScheduledThreadPoolExecutor deadlines;

    @org.springframework.beans.factory.annotation.Autowired
    public HttpChecker(DestinationPolicy policy, Clock clock) {
        this(policy, clock, System::nanoTime);
    }
    HttpChecker(DestinationPolicy policy, Clock clock, LongSupplier ticker) {
        this.policy = policy;
        this.clock = clock;
        this.ticker = ticker;
        // Fresh connections ensure every check passes through destination validation.
        client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(64).setMaxConnPerRoute(64).setDnsResolver(policy).build())
                .setConnectionReuseStrategy((request, response, context) -> false)
                .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement()
                .build();
        deadlines = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("check-deadline-", 0).factory());
        deadlines.setRemoveOnCancelPolicy(true);
    }
    public CheckOutcome check(MonitorSnapshot monitor) {
        Instant checkedAt = clock.instant();
        long start = ticker.getAsLong();
        AtomicBoolean expired = new AtomicBoolean();
        HttpGet request;
        try {
            request = new HttpGet(policy.parse(monitor.url()));
        } catch (DestinationPolicy.InvalidDestination e) {
            return failure(checkedAt, start, FailureReason.BLOCKED_DESTINATION);
        }
        Timeout timeout = Timeout.ofMilliseconds(monitor.timeoutMs());
        request.setConfig(RequestConfig.custom().setConnectionRequestTimeout(timeout)
                .setConnectTimeout(timeout).setResponseTimeout(timeout).build());
        ScheduledFuture<?> deadline = deadlines.schedule(() -> {
            expired.set(true);
            request.cancel();
        }, monitor.timeoutMs(), TimeUnit.MILLISECONDS);
        try {
            // Also validates literal IPs, which some connection operators do not send to DnsResolver.
            policy.validate(monitor.url());
            if (expired.get()) return failure(checkedAt, start, FailureReason.TIMEOUT);
            var response = CloseableHttpResponse.adapt(client.executeOpen(null, request, null));
            try {
                long duration = elapsed(start);
                int status = response.getCode();
                // Measure time to response headers; never buffer untrusted response bodies.
                if (expired.get()) return failure(checkedAt, start, FailureReason.TIMEOUT);
                HealthStatus health = status < 200 || status >= 300 ? HealthStatus.DOWN
                        : duration > monitor.degradationThresholdMs() ? HealthStatus.DEGRADED : HealthStatus.UP;
                return new CheckOutcome(checkedAt, status, duration, health,
                        health == HealthStatus.DOWN ? FailureReason.HTTP_ERROR : null);
            } finally {
                // Close exactly once, immediately. A second graceful close may try to
                // drain the body and turn a successful header response into an I/O error.
                response.close(CloseMode.IMMEDIATE);
            }
        } catch (IOException e) {
            return failure(checkedAt, start, expired.get() ? FailureReason.TIMEOUT : classify(e));
        } finally {
            deadline.cancel(false);
        }
    }
    static FailureReason classify(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof DestinationPolicy.BlockedDestination) return FailureReason.BLOCKED_DESTINATION;
            if (cause instanceof java.net.SocketTimeoutException || cause instanceof org.apache.hc.core5.http.ConnectionRequestTimeoutException)
                return FailureReason.TIMEOUT;
            if (cause instanceof UnknownHostException) return FailureReason.DNS_FAILURE;
            if (cause instanceof ConnectException || cause instanceof NoRouteToHostException) return FailureReason.CONNECTION_FAILURE;
            if (cause instanceof SSLException) return FailureReason.TLS_FAILURE;
        }
        return FailureReason.NETWORK_ERROR;
    }
    private long elapsed(long start) { return Math.max(0, TimeUnit.NANOSECONDS.toMillis(ticker.getAsLong() - start)); }
    private CheckOutcome failure(Instant at, long start, FailureReason reason) {
        return new CheckOutcome(at, null, elapsed(start), HealthStatus.DOWN, reason);
    }
    @PreDestroy public void close() throws IOException {
        deadlines.shutdownNow();
        client.close(CloseMode.IMMEDIATE);
    }
}
