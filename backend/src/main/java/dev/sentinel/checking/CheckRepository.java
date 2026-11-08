package dev.sentinel.checking;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CheckRepository extends JpaRepository<CheckResult, UUID> {
    Page<CheckResult> findByMonitorId(UUID monitorId, Pageable pageable);
}
