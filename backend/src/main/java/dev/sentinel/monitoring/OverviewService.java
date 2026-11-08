package dev.sentinel.monitoring;

import dev.sentinel.checking.*;
import dev.sentinel.incidents.*;
import dev.sentinel.shared.PageResponse;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OverviewService {
    private final MonitorRepository monitors;
    private final CheckRepository checks;
    private final IncidentRepository incidents;
    private final Clock clock;
    public OverviewService(MonitorRepository monitors, CheckRepository checks, IncidentRepository incidents, Clock clock) {
        this.monitors = monitors; this.checks = checks; this.incidents = incidents; this.clock = clock;
    }
    public record Activity(String monitorName, CheckResponse check) {}
    public record Overview(long total, long up, long degraded, long down, long pending,
                           long activeIncidents, List<Activity> recentActivity) {}
    public Overview overview() {
        long total = 0, up = 0, degraded = 0, down = 0, pending = 0;
        for (var count : monitors.countStatuses()) {
            total += count.getTotal();
            if (count.getStatus() == null) pending = count.getTotal();
            else switch (count.getStatus()) {
                case UP -> up = count.getTotal();
                case DEGRADED -> degraded = count.getTotal();
                case DOWN -> down = count.getTotal();
            }
        }
        var recent = checks.findAll(PageRequest.of(0, 12, Sort.by("checkedAt", "id").descending())).getContent();
        var names = names(recent.stream().map(CheckResult::getMonitorId).toList());
        return new Overview(total, up, degraded, down, pending, incidents.countByRecoveredAtIsNull(),
                recent.stream().map(c -> new Activity(names.getOrDefault(c.getMonitorId(), "Deleted monitor"), CheckResponse.from(c))).toList());
    }
    public PageResponse<IncidentResponse> incidents(int page, int size) {
        var results = incidents.findAll(PageRequest.of(page, size, Sort.by("startedAt", "id").descending()));
        var names = names(results.stream().map(Incident::getMonitorId).toList());
        return PageResponse.from(results.map(i -> IncidentResponse.from(i, names.getOrDefault(i.getMonitorId(), "Deleted monitor"), clock.instant())));
    }
    private Map<UUID, String> names(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return monitors.findAllById(new HashSet<>(ids)).stream().collect(Collectors.toMap(Monitor::getId, Monitor::getName));
    }
}
