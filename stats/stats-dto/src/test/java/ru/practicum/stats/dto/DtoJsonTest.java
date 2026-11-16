package ru.practicum.stats.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DtoJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void hitUsesSpecifiedDateFormatAndIgnoresInputId() throws Exception {
        EndpointHit hit = mapper.readValue("{\"id\":999,\"app\":\"ewm\",\"uri\":\"/events/1\",\"ip\":\"::1\",\"timestamp\":\"2026-11-10 12:00:00\"}", EndpointHit.class);
        assertThat(hit.getId()).isNull();
        assertThat(hit.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 11, 10, 12, 0));
        assertThat(mapper.readTree(mapper.writeValueAsString(hit)).get("timestamp").asText())
                .isEqualTo("2026-11-10 12:00:00");
    }

    @Test
    void statsUsesLongCounter() throws Exception {
        ViewStats stats = new ViewStats("ewm", "/events", 3_000_000_000L);
        assertThat(mapper.readValue(mapper.writeValueAsString(stats), ViewStats.class)).isEqualTo(stats);
    }
}
