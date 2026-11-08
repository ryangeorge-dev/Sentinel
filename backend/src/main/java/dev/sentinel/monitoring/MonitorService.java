package dev.sentinel.monitoring;

import dev.sentinel.checking.DestinationPolicy;
import dev.sentinel.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.*;

@Service
public class MonitorService {
    private final MonitorRepository monitors;
    private final DestinationPolicy policy;
    private final Clock clock;
    public MonitorService(MonitorRepository monitors, DestinationPolicy policy, Clock clock) {
        this.monitors = monitors;
        this.policy = policy;
        this.clock = clock;
    }
    public void validateDestination(String url) {
        try { policy.validate(url); }
        catch (DestinationPolicy.BlockedDestination e) {
            throw new DestinationPolicy.InvalidDestination("Destination must resolve only to public addresses");
        } catch (UnknownHostException e) {
            throw new DestinationPolicy.InvalidDestination("Destination hostname could not be resolved");
        }
    }
    @Transactional public MonitorResponse create(MonitorRequest request) {
        return MonitorResponse.from(monitors.save(new Monitor(request, clock.instant())));
    }
    @Transactional public MonitorResponse update(UUID id, MonitorRequest request) {
        Monitor monitor = monitors.lockById(id).orElseThrow(NotFoundException::new);
        monitor.configure(request, clock.instant());
        return MonitorResponse.from(monitor);
    }
    @Transactional public MonitorResponse enable(UUID id, boolean enabled) {
        Monitor monitor = monitors.lockById(id).orElseThrow(NotFoundException::new);
        monitor.setEnabled(enabled, clock.instant());
        return MonitorResponse.from(monitor);
    }
    @Transactional public void delete(UUID id) {
        Monitor monitor = monitors.lockById(id).orElseThrow(NotFoundException::new);
        monitors.delete(monitor);
    }
    @Transactional(readOnly = true) public MonitorResponse get(UUID id) {
        return MonitorResponse.from(require(id));
    }
    public Monitor require(UUID id) { return monitors.findById(id).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly = true) public Optional<MonitorSnapshot> enabledSnapshot(UUID id) {
        return monitors.findById(id).filter(Monitor::getEnabled).map(Monitor::snapshot);
    }
}
