package dev.sentinel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sentinel.checking.*;
import dev.sentinel.incidents.*;
import dev.sentinel.monitoring.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"sentinel.scheduler.enabled=false", "sentinel.security.approved-local-hosts=localhost,127.0.0.1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SentinelIT {
    static PostgreSQLContainer<?> postgres;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        // An explicitly supplied disposable database supports local verification without Docker.
        // CI and the default `mvn verify` path always use a real PostgreSQL Testcontainer.
        String external = System.getProperty("sentinel.test.db-url");
        if (external == null) {
            postgres = new PostgreSQLContainer<>("postgres:17-alpine");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> external);
            registry.add("spring.datasource.username", () -> System.getProperty("sentinel.test.db-user", "sentinel"));
            registry.add("spring.datasource.password", () -> System.getProperty("sentinel.test.db-password", ""));
        }
    }
    @AfterAll static void stop() { if (postgres != null) postgres.stop(); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MonitorService service;
    @Autowired MonitorRepository monitors;
    @Autowired CheckRepository checks;
    @MockitoSpyBean IncidentRepository incidents;
    @Autowired ResultRecorder recorder;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clean() { jdbc.execute("TRUNCATE monitors CASCADE"); }
    MonitorResponse create() {
        return service.create(new MonitorRequest("Local API", "http://localhost:9999", 5, 1000, 200, true));
    }
    CheckOutcome down(int seconds) { return new CheckOutcome(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(seconds), null, 1000, HealthStatus.DOWN, FailureReason.TIMEOUT); }
    MonitorSnapshot snapshot(UUID id) { return service.enabledSnapshot(id).orElseThrow(); }
    @Test void migrationCrudAndHistoricalApiWork() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Long.class)).isEqualTo(2L);
        String body = """
                {"name":"Local API","url":"http://localhost:9999","intervalSeconds":5,"timeoutMs":1000,"degradationThresholdMs":200,"enabled":true}
                """;
        var response = mvc.perform(post("/api/monitors").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
        UUID id = UUID.fromString(json.readTree(response.getResponse().getContentAsString()).get("id").asText());
        mvc.perform(get("/api/monitors")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/monitors/" + id)).andExpect(jsonPath("$.currentStatus").isEmpty());
        for (int n = 0; n < 3; n++) recorder.record(snapshot(id), down(n));
        mvc.perform(get("/api/monitors/" + id + "/checks?size=2"))
                .andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].httpStatus").isEmpty()).andExpect(jsonPath("$.content[0].failureReason").value("TIMEOUT"));
        mvc.perform(get("/api/monitors/" + id + "/incidents"))
                .andExpect(jsonPath("$.content[0].active").value(true)).andExpect(jsonPath("$.content[0].startedAt").value("2026-01-01T00:00:00Z"));
        mvc.perform(get("/api/dashboard")).andExpect(jsonPath("$.down").value(1)).andExpect(jsonPath("$.activeIncidents").value(1));
        mvc.perform(get("/api/incidents")).andExpect(jsonPath("$.content[0].monitorName").value("Local API"));
        mvc.perform(put("/api/monitors/" + id).contentType("application/json").content(body.replace("Local API", "Renamed")))
                .andExpect(jsonPath("$.name").value("Renamed"));
        mvc.perform(patch("/api/monitors/" + id + "/enabled").contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(delete("/api/monitors/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/monitors/" + id)).andExpect(status().isNotFound());
        assertThat(checks.count()).isZero(); assertThat(incidents.count()).isZero();
    }
    @Test void concurrentFailuresSerializeAndOpenOnlyOneIncidentThenRecover() throws Exception {
        var monitor = create(); var snapshot = snapshot(monitor.id());
        CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int n = 0; n < 8; n++) tasks.add(executor.submit(() -> {
                ready.countDown();
                try { assertThat(start.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                recorder.record(snapshot, down(0));
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            for (var task : tasks) task.get(15, TimeUnit.SECONDS);
        }
        assertThat(checks.count()).isEqualTo(8);
        assertThat(incidents.count()).isEqualTo(1);
        assertThat(monitors.findById(monitor.id()).orElseThrow().getConsecutiveFailures()).isEqualTo(3);
        recorder.record(snapshot, new CheckOutcome(down(30).checkedAt(), 200, 300, HealthStatus.DEGRADED, null));
        Incident recovered = incidents.findAll().getFirst();
        assertThat(recovered.getRecoveredAt()).isEqualTo(down(30).checkedAt());
        assertThat(IncidentResponse.from(recovered, monitor.name(), Instant.now()).durationSeconds()).isEqualTo(30);
        for (int n = 40; n < 43; n++) recorder.record(snapshot, down(n));
        assertThat(incidents.count()).isEqualTo(2);
    }
    @Test void uniqueIndexPreventsDuplicateOpenIncidentEvenOutsideService() {
        UUID id = create().id();
        incidents.saveAndFlush(new Incident(id, Instant.now()));
        assertThatThrownBy(() -> incidents.saveAndFlush(new Incident(id, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void incidentPersistenceFailureRollsBackCheckAndFailureCounter() {
        var monitor = create(); var snapshot = snapshot(monitor.id());
        recorder.record(snapshot, down(0)); recorder.record(snapshot, down(1));
        doThrow(new IllegalStateException("injected persistence failure")).when(incidents).save(any(Incident.class));
        assertThatThrownBy(() -> recorder.record(snapshot, down(2))).isInstanceOf(IllegalStateException.class);
        assertThat(checks.count()).isEqualTo(2);
        assertThat(monitors.findById(monitor.id()).orElseThrow().getConsecutiveFailures()).isEqualTo(2);
        assertThat(incidents.count()).isZero();
    }
    @Test void disabledAndEditedInflightChecksAreDiscarded() {
        var monitor = create(); var old = snapshot(monitor.id());
        service.enable(monitor.id(), false); recorder.record(old, down(0));
        service.enable(monitor.id(), true); recorder.record(old, down(1));
        assertThat(checks.count()).isZero();
        service.delete(monitor.id()); recorder.record(old, down(2));
        assertThat(checks.count()).isZero();
    }
    @Test void restRejectsNonAllowlistedPrivateAddresses() throws Exception {
        mvc.perform(post("/api/monitors").contentType("application/json").content("""
            {"name":"Blocked","url":"http://169.254.169.254/latest/meta-data","intervalSeconds":5,"timeoutMs":1000,"degradationThresholdMs":200,"enabled":true}
            """)) .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DESTINATION"));
    }
    @Test void databaseRejectsInconsistentHealthAndHttpStatus() {
        UUID id = create().id();
        assertThatThrownBy(() -> checks.saveAndFlush(new CheckResult(id,
                new CheckOutcome(Instant.now(), null, 10, HealthStatus.UP, null))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> checks.saveAndFlush(new CheckResult(id,
                new CheckOutcome(Instant.now(), 200, 10, HealthStatus.DOWN, FailureReason.HTTP_ERROR))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}
