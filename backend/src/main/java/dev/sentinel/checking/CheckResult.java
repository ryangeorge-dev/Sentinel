package dev.sentinel.checking;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "check_results")
public class CheckResult {
    @Id private UUID id;
    private UUID monitorId;
    private Instant checkedAt;
    private Integer httpStatus;
    private long durationMs;
    @Enumerated(EnumType.STRING) private HealthStatus status;
    @Enumerated(EnumType.STRING) private FailureReason failureReason;
    protected CheckResult() {}
    public CheckResult(UUID monitorId, CheckOutcome outcome) {
        id = UUID.randomUUID();
        this.monitorId = monitorId;
        checkedAt = outcome.checkedAt();
        httpStatus = outcome.httpStatus();
        durationMs = outcome.durationMs();
        status = outcome.status();
        failureReason = outcome.failureReason();
    }
    public UUID getId() { return id; }
    public UUID getMonitorId() { return monitorId; }
    public Instant getCheckedAt() { return checkedAt; }
    public Integer getHttpStatus() { return httpStatus; }
    public long getDurationMs() { return durationMs; }
    public HealthStatus getStatus() { return status; }
    public FailureReason getFailureReason() { return failureReason; }
}
