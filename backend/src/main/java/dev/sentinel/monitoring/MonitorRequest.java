package dev.sentinel.monitoring;

import jakarta.validation.constraints.*;

public record MonitorRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 2048) String url,
        @NotNull @Min(5) @Max(86400) Integer intervalSeconds,
        @NotNull @Min(100) @Max(30000) Integer timeoutMs,
        @NotNull @Min(1) @Max(30000) Integer degradationThresholdMs,
        @NotNull Boolean enabled) {
    @Override public String toString() { return "MonitorRequest[redacted]"; }
    @AssertTrue(message = "Degradation threshold must not exceed request timeout")
    public boolean isThresholdWithinTimeout() {
        return timeoutMs == null || degradationThresholdMs == null || degradationThresholdMs <= timeoutMs;
    }
}
