package ru.practicum.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.stats.repository.HitRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class StatsApiTest {
    private static final String START = "2026-11-10 00:00:00";

    private static final String END = "2026-11-10 23:59:59";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private HitRepository repository;

    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void savesEveryVisitAndGeneratesId() throws Exception {
        save("ewm", "/events/1", "::1", START);
        save("ewm", "/events/1", "::1", START);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findAll()).allSatisfy(hit -> {
            assertThat(hit.getId()).isPositive();
            assertThat(hit.getId()).isNotEqualTo(999L);
            assertThat(hit.getApp()).isEqualTo("ewm");
        });
    }

    @Test
    void countsAllAndUniqueVisitsWithinAppAndUri() throws Exception {
        save("ewm", "/events/1", "10.0.0.1", START);
        save("ewm", "/events/1", "10.0.0.1", START);
        save("ewm", "/events/1", "10.0.0.2", END);
        save("other", "/events/1", "10.0.0.1", START);
        save("ewm", "/events/2", "10.0.0.1", START);

        mvc.perform(get("/stats").param("start", START).param("end", END))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].app").value("ewm"))
                .andExpect(jsonPath("$[0].uri").value("/events/1"))
                .andExpect(jsonPath("$[0].hits").value(3));

        mvc.perform(get("/stats").param("start", START).param("end", END).param("unique", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].hits").value(2))
                .andExpect(jsonPath("$[1].hits").value(1))
                .andExpect(jsonPath("$[2].hits").value(1));
    }

    @Test
    void filtersTimeAndRepeatedUriParametersIncludingBothBoundaries() throws Exception {
        save("ewm", "/events/1", "::1", START);
        save("ewm", "/events/2", "::1", END);
        save("ewm", "/events/3", "::1", START);
        save("ewm", "/events/1", "::1", "2026-11-09 23:59:59");
        save("ewm", "/events/1", "::1", "2026-11-11 00:00:00");
        mvc.perform(get("/stats").param("start", START).param("end", END)
                        .param("uris", "/events/1", "/events/2").param("unique", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].hits").value(1))
                .andExpect(jsonPath("$[1].hits").value(1));
        mvc.perform(get("/stats").param("start", START).param("end", START).param("uris", "/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hits").value(1));
    }

    @Test
    void returnsEmptyArrayForMissingData() throws Exception {
        mvc.perform(get("/stats").param("start", START).param("end", END))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        save("ewm", "/events/1", "::1", START);
        mvc.perform(get("/stats").param("start", START).param("end", END).param("uris", "/unknown"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void rejectsInvalidQueryParameters() throws Exception {
        mvc.perform(get("/stats").param("start", END).param("end", START))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        mvc.perform(get("/stats").param("end", END)).andExpect(status().isBadRequest());
        mvc.perform(get("/stats").param("start", "not-a-date").param("end", END))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/stats").param("start", START).param("end", END).param("unique", "wrong"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidHitWithoutWritingToDatabase() throws Exception {
        mvc.perform(post("/hit").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/hit").contentType(MediaType.APPLICATION_JSON).content("{\"app\":\" \",\"uri\":\"/events\",\"ip\":\"::1\",\"timestamp\":\"2026-11-10 12:00:00\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/hit").contentType(MediaType.APPLICATION_JSON).content("{\"app\":\"ewm\",\"uri\":\"/events\",\"ip\":\"::1\",\"timestamp\":\"wrong\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/hit").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void exposesActuatorHealth() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    private void save(String app, String uri, String ip, String timestamp) throws Exception {
        mvc.perform(post("/hit").contentType(MediaType.APPLICATION_JSON).content("{\"id\":999,\"app\":\"%s\",\"uri\":\"%s\",\"ip\":\"%s\",\"timestamp\":\"%s\"}".formatted(app, uri, ip, timestamp))).andExpect(status().isCreated());
    }
}
