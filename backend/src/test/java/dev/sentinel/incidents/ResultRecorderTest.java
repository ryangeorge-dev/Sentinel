package dev.sentinel.incidents;

import dev.sentinel.checking.*;
import dev.sentinel.monitoring.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResultRecorderTest {
    final MonitorRepository monitors = mock(MonitorRepository.class);
    final CheckRepository checks = mock(CheckRepository.class);
    final IncidentRepository incidents = mock(IncidentRepository.class);
    final Instant first = Instant.parse("2026-01-01T00:00:00Z");
    final Monitor monitor = new Monitor(new MonitorRequest("API", "https://example.com", 10, 1000, 100, true), first);
    final ResultRecorder recorder = new ResultRecorder(monitors, checks, incidents, Clock.fixed(first, ZoneOffset.UTC));
    @BeforeEach void setup() {
        when(monitors.lockById(monitor.getId())).thenReturn(Optional.of(monitor));
        when(incidents.findByMonitorIdAndRecoveredAtIsNull(monitor.getId())).thenReturn(Optional.empty());
    }
    void record(HealthStatus status, int seconds) {
        recorder.record(monitor.snapshot(), new CheckOutcome(first.plusSeconds(seconds), status == HealthStatus.DOWN ? null : 200,
                10, status, status == HealthStatus.DOWN ? FailureReason.TIMEOUT : null));
    }
    @Test void opensOnThirdDownStartingAtFirstFailure() {
        record(HealthStatus.DOWN, 0); record(HealthStatus.DOWN, 10);
        verify(incidents, never()).save(any());
        record(HealthStatus.DOWN, 20);
        var captured = ArgumentCaptor.forClass(Incident.class);
        verify(incidents).save(captured.capture());
        assertThat(captured.getValue().getStartedAt()).isEqualTo(first);
        verify(checks, times(3)).save(any());
    }
    @Test void degradedBreaksConsecutiveSequence() {
        record(HealthStatus.DOWN, 0); record(HealthStatus.DOWN, 10); record(HealthStatus.DEGRADED, 20); record(HealthStatus.DOWN, 30);
        verify(incidents, never()).save(any());
        assertThat(monitor.getConsecutiveFailures()).isEqualTo(1);
        assertThat(monitor.getFirstFailureAt()).isEqualTo(first.plusSeconds(30));
    }
    @Test void activeIncidentIsNotDuplicatedAndClosesOnDegraded() {
        var active = new Incident(monitor.getId(), first);
        when(incidents.findByMonitorIdAndRecoveredAtIsNull(monitor.getId())).thenReturn(Optional.of(active));
        for (int i = 0; i < 10; i++) record(HealthStatus.DOWN, i);
        verify(incidents, never()).save(any());
        assertThat(monitor.getConsecutiveFailures()).isEqualTo(3);
        record(HealthStatus.DEGRADED, 20);
        assertThat(active.getRecoveredAt()).isEqualTo(first.plusSeconds(20));
        assertThat(monitor.getConsecutiveFailures()).isZero();
    }
    @Test void upRecoversAndResetsCounter() {
        var active = new Incident(monitor.getId(), first);
        when(incidents.findByMonitorIdAndRecoveredAtIsNull(monitor.getId())).thenReturn(Optional.of(active));
        record(HealthStatus.DOWN, 0); record(HealthStatus.UP, 10);
        assertThat(active.getRecoveredAt()).isEqualTo(first.plusSeconds(10));
        assertThat(monitor.getFirstFailureAt()).isNull();
    }
    @Test void editedDisabledAndDeletedMonitorsDiscardInflightResults() {
        var snapshot = monitor.snapshot();
        monitor.setEnabled(false, first);
        var outcome = new CheckOutcome(first, 200, 10, HealthStatus.UP, null);
        recorder.record(snapshot, outcome);
        monitor.setEnabled(true, first);
        recorder.record(snapshot, outcome);
        when(monitors.lockById(monitor.getId())).thenReturn(Optional.empty());
        recorder.record(snapshot, outcome);
        verifyNoInteractions(checks, incidents);
    }
}
