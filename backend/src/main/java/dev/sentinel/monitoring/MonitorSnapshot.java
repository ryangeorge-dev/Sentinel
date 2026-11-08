package dev.sentinel.monitoring;

import java.util.UUID;

public record MonitorSnapshot(UUID id, String url, int timeoutMs, int degradationThresholdMs, long configRevision) {
    // URLs can contain secrets. Do not let record-generated toString expose them.
    @Override public String toString() { return "MonitorSnapshot[id=" + id + "]"; }
}
