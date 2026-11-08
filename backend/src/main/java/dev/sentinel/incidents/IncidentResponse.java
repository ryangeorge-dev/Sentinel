package dev.sentinel.incidents;

import java.time.*;
import java.util.UUID;

public record IncidentResponse(UUID id, UUID monitorId, String monitorName, Instant startedAt,
                               Instant recoveredAt, long durationSeconds, boolean active) {
    public static IncidentResponse from(Incident i, String name, Instant now) {
        return new IncidentResponse(i.getId(), i.getMonitorId(), name, i.getStartedAt(), i.getRecoveredAt(),
            Math.max(0, Duration.between(i.getStartedAt(), i.getRecoveredAt() == null ? now : i.getRecoveredAt()).toSeconds()),
            i.getRecoveredAt() == null);
    }
}
