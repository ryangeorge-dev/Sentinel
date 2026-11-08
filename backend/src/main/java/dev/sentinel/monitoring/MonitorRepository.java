package dev.sentinel.monitoring;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface MonitorRepository extends JpaRepository<Monitor, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Monitor m where m.id = :id")
    Optional<Monitor> lockById(@Param("id") UUID id);

    @Query("select m.id from Monitor m where m.enabled = true and m.nextCheckAt <= :now order by m.nextCheckAt, m.id")
    List<UUID> findDue(@Param("now") Instant now, Pageable limit);

    @Query("select m.latestStatus as status, count(m) as total from Monitor m group by m.latestStatus")
    List<StatusCount> countStatuses();
    interface StatusCount {
        dev.sentinel.checking.HealthStatus getStatus();
        long getTotal();
    }
}
