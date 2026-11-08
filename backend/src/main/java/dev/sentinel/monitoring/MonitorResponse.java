package dev.sentinel.monitoring;

import dev.sentinel.checking.HealthStatus;
import java.time.Instant;
import java.util.UUID;

public record MonitorResponse(UUID id, String name, String url, int intervalSeconds, int timeoutMs,
        int degradationThresholdMs, boolean enabled, Instant createdAt, Instant updatedAt,
        HealthStatus currentStatus, Instant lastCheckedAt, Long latestDurationMs, Integer latestHttpStatus) {
    public static MonitorResponse from(Monitor m) {
        return new MonitorResponse(m.getId(), m.getName(), m.getUrl(), m.getIntervalSeconds(), m.getTimeoutMs(),
            m.getDegradationThresholdMs(), m.getEnabled(), m.getCreatedAt(), m.getUpdatedAt(),
            m.getLatestStatus(), m.getLastCheckedAt(), m.getLatestDurationMs(), m.getLatestHttpStatus());
    }
}
