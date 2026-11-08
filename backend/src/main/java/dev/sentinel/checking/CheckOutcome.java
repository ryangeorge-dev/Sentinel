package dev.sentinel.checking;

import java.time.Instant;

public record CheckOutcome(Instant checkedAt, Integer httpStatus, long durationMs,
                           HealthStatus status, FailureReason failureReason) {}
