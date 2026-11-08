package dev.sentinel.monitoring;

import dev.sentinel.incidents.IncidentResponse;
import dev.sentinel.shared.PageResponse;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OverviewController {
    private final OverviewService service;
    public OverviewController(OverviewService service) { this.service = service; }
    @GetMapping("/dashboard") public OverviewService.Overview dashboard() { return service.overview(); }
    @GetMapping("/incidents") public PageResponse<IncidentResponse> incidents(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.incidents(page, size);
    }
}
