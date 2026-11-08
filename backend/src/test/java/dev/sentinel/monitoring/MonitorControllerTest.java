package dev.sentinel.monitoring;

import dev.sentinel.checking.CheckRepository;
import dev.sentinel.incidents.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Clock;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MonitorController.class)
class MonitorControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MonitorService service;
    @MockitoBean MonitorRepository monitors;
    @MockitoBean CheckRepository checks;
    @MockitoBean IncidentRepository incidents;
    @MockitoBean Clock clock;
    @Test void validatesRequestFieldsWithoutMakingNetworkRequests() throws Exception {
        mvc.perform(post("/api/monitors").contentType("application/json").content("""
                {"name":"", "url":"", "intervalSeconds":1, "timeoutMs":99, "degradationThresholdMs":100, "enabled":true}
                """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.name").exists()).andExpect(jsonPath("$.errors.intervalSeconds").exists())
            .andExpect(jsonPath("$.errors.thresholdWithinTimeout").exists());
        verifyNoInteractions(service);
    }
    @Test void rejectsMalformedJsonAndPaginationAndId() throws Exception {
        mvc.perform(post("/api/monitors").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/monitors?size=100000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/monitors?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/monitors/not-a-uuid")).andExpect(status().isBadRequest());
    }
    @Test void enableRequiresExplicitBoolean() throws Exception {
        mvc.perform(patch("/api/monitors/00000000-0000-0000-0000-000000000001/enabled").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.enabled").exists());
    }
    @Test void rejectsFractionalIntervalsAndUnsupportedContentType() throws Exception {
        mvc.perform(post("/api/monitors").contentType("application/json").content("""
            {"name":"API","url":"https://example.com","intervalSeconds":5.5,"timeoutMs":1000,"degradationThresholdMs":200,"enabled":true}
            """)) .andExpect(status().isBadRequest());
        mvc.perform(post("/api/monitors").contentType("text/plain").content("hello"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(service);
    }

}
