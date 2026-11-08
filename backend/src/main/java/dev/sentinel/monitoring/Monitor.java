package dev.sentinel.monitoring;

import dev.sentinel.checking.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "monitors")
public class Monitor {
    @Id
    private UUID id;
    private String name;
    private String url;
    private int intervalSeconds;
    private int timeoutMs;
    private int degradationThresholdMs;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
    private long configRevision;
    private Instant nextCheckAt;
    private int consecutiveFailures;
    private Instant firstFailureAt;
    @Enumerated(EnumType.STRING)
    private HealthStatus latestStatus;
    private Instant lastCheckedAt;
    private Long latestDurationMs;
    private Integer latestHttpStatus;

    protected Monitor() {}
    public Monitor(MonitorRequest request, Instant now) {
        id = UUID.randomUUID();
        createdAt = now;
        configure(request, now);
    }
    public void configure(MonitorRequest request, Instant now) {
        name = request.name().strip();
        url = request.url();
        intervalSeconds = request.intervalSeconds();
        timeoutMs = request.timeoutMs();
        degradationThresholdMs = request.degradationThresholdMs();
        enabled = request.enabled();
        updatedAt = now;
        nextCheckAt = now;
        configRevision++;
    }
    public void setEnabled(boolean value, Instant now) {
        enabled = value;
        updatedAt = now;
        nextCheckAt = now;
        configRevision++;
    }
    public void record(CheckOutcome result, Instant completedAt) {
        lastCheckedAt = result.checkedAt();
        latestStatus = result.status();
        latestDurationMs = result.durationMs();
        latestHttpStatus = result.httpStatus();
        nextCheckAt = completedAt.plusSeconds(intervalSeconds);
        if (result.status() == HealthStatus.DOWN) {
            if (consecutiveFailures == 0) firstFailureAt = result.checkedAt();
            consecutiveFailures = Math.min(3, consecutiveFailures + 1);
        } else {
            consecutiveFailures = 0;
            firstFailureAt = null;
        }
    }
    public MonitorSnapshot snapshot() {
        return new MonitorSnapshot(id, url, timeoutMs, degradationThresholdMs, configRevision);
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public int getTimeoutMs() { return timeoutMs; }
    public int getDegradationThresholdMs() { return degradationThresholdMs; }
    public boolean getEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getConfigRevision() { return configRevision; }
    public Instant getNextCheckAt() { return nextCheckAt; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public Instant getFirstFailureAt() { return firstFailureAt; }
    public HealthStatus getLatestStatus() { return latestStatus; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public Long getLatestDurationMs() { return latestDurationMs; }
    public Integer getLatestHttpStatus() { return latestHttpStatus; }
}
