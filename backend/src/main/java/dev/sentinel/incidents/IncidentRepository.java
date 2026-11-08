package dev.sentinel.incidents;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    Optional<Incident> findByMonitorIdAndRecoveredAtIsNull(UUID monitorId);
    Page<Incident> findByMonitorId(UUID monitorId, Pageable pageable);
    long countByRecoveredAtIsNull();
}
