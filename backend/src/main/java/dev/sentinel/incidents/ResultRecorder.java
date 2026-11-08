package dev.sentinel.incidents;

import dev.sentinel.checking.*;
import dev.sentinel.monitoring.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
public class ResultRecorder {
    private final MonitorRepository monitors;
    private final CheckRepository checks;
    private final IncidentRepository incidents;
    private final Clock clock;
    public ResultRecorder(MonitorRepository monitors, CheckRepository checks, IncidentRepository incidents, Clock clock) {
        this.monitors = monitors;
        this.checks = checks;
        this.incidents = incidents;
        this.clock = clock;
    }
    @Transactional
    public void record(MonitorSnapshot snapshot, CheckOutcome outcome) {
        monitors.lockById(snapshot.id()).ifPresent(monitor -> {
            // Configuration changes invalidate in-flight observations. Deleted/disabled
            // monitors must not acquire new history after their mutation commits.
            if (!monitor.getEnabled() || monitor.getConfigRevision() != snapshot.configRevision()) return;
            monitor.record(outcome, clock.instant());
            checks.save(new CheckResult(monitor.getId(), outcome));
            var active = incidents.findByMonitorIdAndRecoveredAtIsNull(monitor.getId());
            if (outcome.status() == HealthStatus.DOWN) {
                if (monitor.getConsecutiveFailures() >= 3 && active.isEmpty()) {
                    incidents.save(new Incident(monitor.getId(), monitor.getFirstFailureAt()));
                }
            } else {
                active.ifPresent(incident -> incident.recover(outcome.checkedAt()));
            }
        });
    }
}
