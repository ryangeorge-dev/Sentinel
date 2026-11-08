package dev.sentinel.scheduling;

import dev.sentinel.checking.*;
import dev.sentinel.incidents.ResultRecorder;
import dev.sentinel.monitoring.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CheckSchedulerTest {
    private final MonitorRepository repository = mock(MonitorRepository.class);
    private final MonitorService monitors = mock(MonitorService.class);
    private final HttpChecker checker = mock(HttpChecker.class);
    private final ResultRecorder recorder = mock(ResultRecorder.class);
    private CheckScheduler scheduler;
    private final CountDownLatch release = new CountDownLatch(1);
    @AfterEach void cleanup() { release.countDown(); if (scheduler != null) scheduler.close(); }
    private void create(int workers, int queue) {
        scheduler = new CheckScheduler(new CheckScheduler.Settings(workers, queue, Duration.ofSeconds(10)), repository, monitors, checker, recorder, Clock.systemUTC());
        when(monitors.enabledSnapshot(any())).thenAnswer(call -> Optional.of(snapshot(call.getArgument(0))));
    }
    private MonitorSnapshot snapshot(UUID id) { return new MonitorSnapshot(id, "https://example.com", 1000, 100, 0); }
    private CheckOutcome up() { return new CheckOutcome(Instant.now(), 200, 1, HealthStatus.UP, null); }
    @Test void differentMonitorsRunConcurrentlyButSameMonitorCannotOverlap() throws Exception {
        create(2, 2);
        CountDownLatch started = new CountDownLatch(2);
        when(checker.check(any())).thenAnswer(call -> { started.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); return up(); });
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertThat(scheduler.submit(first)).isTrue();
        assertThat(scheduler.submit(first)).isFalse();
        assertThat(scheduler.submit(second)).isTrue();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduler.reservations()).isEqualTo(2);
        release.countDown();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(scheduler.reservations()).isZero());
        verify(checker, times(2)).check(any());
    }
    @Test void saturationAndDuplicateQueuedChecksDoNotLeakReservations() throws Exception {
        create(1, 1);
        CountDownLatch started = new CountDownLatch(1);
        when(checker.check(any())).thenAnswer(call -> { started.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); return up(); });
        UUID first = UUID.randomUUID(), queued = UUID.randomUUID(), rejected = UUID.randomUUID();
        assertThat(scheduler.submit(first)).isTrue();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduler.submit(queued)).isTrue();
        assertThat(scheduler.submit(queued)).isFalse();
        assertThat(scheduler.submit(rejected)).isFalse();
        assertThat(scheduler.reservations()).isEqualTo(2);
        release.countDown();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(scheduler.reservations()).isZero());
        assertThat(scheduler.submit(rejected)).isTrue();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(recorder, times(3)).record(any(), any()));
    }
    @Test void checkerExceptionReleasesStateForNextAttempt() {
        create(1, 1);
        when(checker.check(any())).thenThrow(new IllegalStateException()).thenReturn(up());
        UUID id = UUID.randomUUID();
        scheduler.submit(id);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(scheduler.reservations()).isZero());
        assertThat(scheduler.submit(id)).isTrue();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(recorder).record(any(), any()));
    }
    @Test void staleQueuedTasksAreSkippedWithoutRecordingFailures() throws Exception {
        AtomicLong ticker = new AtomicLong();
        scheduler = new CheckScheduler(new CheckScheduler.Settings(1, 1, Duration.ofSeconds(1)), repository, monitors, checker, recorder, Clock.systemUTC(), ticker::get);
        when(monitors.enabledSnapshot(any())).thenAnswer(call -> Optional.of(snapshot(call.getArgument(0))));
        CountDownLatch started = new CountDownLatch(1);
        when(checker.check(any())).thenAnswer(call -> { started.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); return up(); });
        scheduler.submit(UUID.randomUUID());
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        UUID queued = UUID.randomUUID();
        scheduler.submit(queued);
        ticker.set(Duration.ofSeconds(2).toNanos());
        release.countDown();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(scheduler.reservations()).isZero());
        verify(monitors, never()).enabledSnapshot(queued);
        verify(recorder, times(1)).record(any(), any());
    }
    @Test void disabledAndDeletedMonitorsAreSkippedAndShutdownRejectsNewWork() {
        create(1, 1);
        when(monitors.enabledSnapshot(any())).thenReturn(Optional.empty());
        scheduler.submit(UUID.randomUUID());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(scheduler.reservations()).isZero());
        verifyNoInteractions(checker, recorder);
        scheduler.close();
        assertThat(scheduler.submit(UUID.randomUUID())).isFalse();
        assertThat(scheduler.reservations()).isZero();
    }
    @Test void tickQueriesOnlyBoundedCandidates() {
        create(2, 3);
        when(repository.findDue(any(), any())).thenReturn(List.of());
        scheduler.tick();
        verify(repository).findDue(any(), eq(org.springframework.data.domain.PageRequest.of(0, 10)));
    }
}
