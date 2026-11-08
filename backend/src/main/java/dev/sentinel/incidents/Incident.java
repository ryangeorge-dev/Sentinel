package dev.sentinel.incidents;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class Incident {
    @Id private UUID id;
    private UUID monitorId;
    private Instant startedAt;
    private Instant recoveredAt;
    protected Incident() {}
    public Incident(UUID monitorId, Instant startedAt) {
        id = UUID.randomUUID();
        this.monitorId = monitorId;
        this.startedAt = startedAt;
    }
    public void recover(Instant at) {
        // Wall clocks can move backwards; database duration must remain non-negative.
        recoveredAt = at.isBefore(startedAt) ? startedAt : at;
    }
    public UUID getId() { return id; }
    public UUID getMonitorId() { return monitorId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getRecoveredAt() { return recoveredAt; }
}
