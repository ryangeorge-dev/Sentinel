package dev.sentinel.monitoring;

import dev.sentinel.checking.*;
import dev.sentinel.incidents.*;
import dev.sentinel.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Clock;
import java.util.UUID;

@RestController
@RequestMapping("/api/monitors")
public class MonitorController {
    private final MonitorService service;
    private final MonitorRepository monitors;
    private final CheckRepository checks;
    private final IncidentRepository incidents;
    private final Clock clock;
    public MonitorController(MonitorService service, MonitorRepository monitors, CheckRepository checks,
                             IncidentRepository incidents, Clock clock) {
        this.service = service; this.monitors = monitors; this.checks = checks; this.incidents = incidents; this.clock = clock;
    }
    @GetMapping public PageResponse<MonitorResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(monitors.findAll(PageRequest.of(page, size, Sort.by("createdAt", "id").descending())).map(MonitorResponse::from));
    }
    @PostMapping public ResponseEntity<MonitorResponse> create(@Valid @RequestBody MonitorRequest request) {
        service.validateDestination(request.url());
        MonitorResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/monitors/" + created.id())).body(created);
    }
    @GetMapping("/{id}") public MonitorResponse get(@PathVariable UUID id) { return service.get(id); }
    @PutMapping("/{id}") public MonitorResponse update(@PathVariable UUID id, @Valid @RequestBody MonitorRequest request) {
        service.validateDestination(request.url());
        return service.update(id, request);
    }
    public record EnableRequest(@NotNull Boolean enabled) {}
    @PatchMapping("/{id}/enabled") public MonitorResponse enable(@PathVariable UUID id, @Valid @RequestBody EnableRequest request) {
        return service.enable(id, request.enabled());
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id); return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/checks") public PageResponse<CheckResponse> checks(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        service.get(id);
        return PageResponse.from(checks.findByMonitorId(id, PageRequest.of(page, size, Sort.by("checkedAt", "id").descending())).map(CheckResponse::from));
    }
    @GetMapping("/{id}/incidents") public PageResponse<IncidentResponse> incidents(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Kept as an explicit endpoint so histories remain independently pageable.
        return incidentPage(id, page, size);
    }
    private PageResponse<IncidentResponse> incidentPage(UUID id, int page, int size) {
        String name = service.get(id).name();
        return PageResponse.from(incidents.findByMonitorId(id, PageRequest.of(page, size, Sort.by("startedAt", "id").descending()))
                .map(i -> IncidentResponse.from(i, name, clock.instant())));
    }
}
