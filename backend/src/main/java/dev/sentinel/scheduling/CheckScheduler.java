package dev.sentinel.scheduling;

import dev.sentinel.checking.HttpChecker;
import dev.sentinel.incidents.ResultRecorder;
import dev.sentinel.monitoring.*;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

@Component
@ConditionalOnProperty(name = "sentinel.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class CheckScheduler {
    @Validated
    @ConfigurationProperties("sentinel.scheduler")
    public record Settings(@Min(1) @Max(64) int workers, @Min(1) @Max(1000) int queueCapacity,
                           @NotNull Duration maxQueueAge) {
        public Settings {
            if (maxQueueAge == null || maxQueueAge.isNegative() || maxQueueAge.isZero())
                throw new IllegalArgumentException("max-queue-age must be positive");
        }
    }
    private static final Logger log = LoggerFactory.getLogger(CheckScheduler.class);
    private final Set<UUID> reserved = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final ThreadPoolExecutor workers;
    private final MonitorRepository repository;
    private final MonitorService monitors;
    private final HttpChecker checker;
    private final ResultRecorder recorder;
    private final Clock clock;
    private final LongSupplier ticker;
    private final Settings settings;

    @org.springframework.beans.factory.annotation.Autowired
    public CheckScheduler(Settings settings, MonitorRepository repository, MonitorService monitors,
                          HttpChecker checker, ResultRecorder recorder, Clock clock) {
        this(settings, repository, monitors, checker, recorder, clock, System::nanoTime);
    }
    CheckScheduler(Settings settings, MonitorRepository repository, MonitorService monitors,
                   HttpChecker checker, ResultRecorder recorder, Clock clock, LongSupplier ticker) {
        this.settings = settings;
        this.repository = repository;
        this.monitors = monitors;
        this.checker = checker;
        this.recorder = recorder;
        this.clock = clock;
        this.ticker = ticker;
        workers = new ThreadPoolExecutor(settings.workers(), settings.workers(), 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(settings.queueCapacity()),
                Thread.ofPlatform().name("monitor-check-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }
    @Scheduled(fixedDelayString = "${sentinel.scheduler.tick-ms:1000}")
    public void tick() {
        if (stopping.get()) return;
        // At most capacity candidates can already be reserved. The second capacity
        // permits progress on other monitors without loading the entire table.
        int capacity = settings.workers() + settings.queueCapacity();
        for (UUID id : repository.findDue(clock.instant(), PageRequest.of(0, capacity * 2))) {
            if (stopping.get()) return;
            submit(id);
        }
    }
    boolean submit(UUID id) {
        if (stopping.get() || !reserved.add(id)) return false;
        Work work = new Work(id, ticker.getAsLong());
        try {
            workers.execute(work);
            return true;
        } catch (RejectedExecutionException e) {
            reserved.remove(id);
            return false;
        }
    }
    private final class Work implements Runnable {
        private final UUID id;
        private final long queuedAt;
        private Work(UUID id, long queuedAt) { this.id = id; this.queuedAt = queuedAt; }
        @Override public void run() {
            try {
                if (stopping.get() || ticker.getAsLong() - queuedAt > settings.maxQueueAge().toNanos()) return;
                // Re-read configuration at execution time, after any queue delay.
                monitors.enabledSnapshot(id).ifPresent(snapshot -> recorder.record(snapshot, checker.check(snapshot)));
            } catch (Exception e) {
                // Exception messages may contain URL credentials/query parameters.
                log.warn("Check execution failed for monitor {} ({})", id, e.getClass().getSimpleName());
            } finally {
                reserved.remove(id);
            }
        }
    }
    int reservations() { return reserved.size(); }
    @PreDestroy public void close() {
        if (!stopping.compareAndSet(false, true)) return;
        // Drop queued work and allow already-running checks up to their 30s deadline.
        List<Runnable> pending = new ArrayList<>();
        workers.getQueue().drainTo(pending);
        pending.forEach(task -> reserved.remove(((Work) task).id));
        workers.shutdown();
        try {
            if (!workers.awaitTermination(32, TimeUnit.SECONDS)) {
                workers.shutdownNow().forEach(task -> reserved.remove(((Work) task).id));
            }
        } catch (InterruptedException e) {
            workers.shutdownNow().forEach(task -> reserved.remove(((Work) task).id));
            Thread.currentThread().interrupt();
        }
    }
}
