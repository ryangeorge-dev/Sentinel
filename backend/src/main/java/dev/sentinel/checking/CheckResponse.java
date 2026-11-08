package dev.sentinel.checking;

import java.time.Instant;
import java.util.UUID;

public record CheckResponse(UUID id, UUID monitorId, Instant checkedAt, Integer httpStatus,
                            Long durationMs, HealthStatus status, FailureReason failureReason) {
    public static CheckResponse from(CheckResult c) {
        return new CheckResponse(c.getId(), c.getMonitorId(), c.getCheckedAt(), c.getHttpStatus(),
                c.getDurationMs(), c.getStatus(), c.getFailureReason());
    }
}
