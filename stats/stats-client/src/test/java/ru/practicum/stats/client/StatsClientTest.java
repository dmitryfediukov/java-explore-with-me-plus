package ru.practicum.stats.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StatsClientTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 0, 0);

    private static final LocalDateTime END = START.plusDays(1);

    private MockRestServiceServer server;

    private StatsClient client;

    @BeforeEach
    void setUp() {
        RestTemplate template = new RestTemplate();
        server = MockRestServiceServer.bindTo(template).build();
        client = new StatsClient("http://localhost:9090/", template);
    }

    @Test
    void sendsHitAsJsonAndAcceptsEmptyCreatedResponse() {
        server.expect(requestTo("http://localhost:9090/hit"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"id\":null,\"app\":\"ewm\",\"uri\":\"/events/1\",\"ip\":\"::1\",\"timestamp\":\"2026-11-10 00:00:00\"}"))
                .andRespond(withCreatedEntity(null));
        client.saveHit(new EndpointHit(null, "ewm", "/events/1", "::1", START));
        server.verify();
    }

    @Test
    void encodesDatesAndUriValuesExactlyOnceAndReadsStats() {
        server.expect(requestTo("http://localhost:9090/stats?start=2026-11-10%2000%3A00%3A00"
                        + "&end=2026-11-11%2000%3A00%3A00&unique=true"
                        + "&uris=%2Fevents%2F1&uris=%2Fevents%2Fa%2Bb%3Fx%3D1%26y%3D2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"app":"ewm","uri":"/events/1","hits":3}]
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.getStats(START, END, List.of("/events/1", "/events/a+b?x=1&y=2"), true))
                .containsExactly(new ViewStats("ewm", "/events/1", 3L));
        server.verify();
    }

    @Test
    void omitsNullAndEmptyUriListAndReadsEmptyArray() {
        String address = "http://localhost:9090/stats?start=2026-11-10%2000%3A00%3A00"
                + "&end=2026-11-11%2000%3A00%3A00&unique=false";
        server.expect(requestTo(address)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(address)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(client.getStats(START, END, null, false)).isEmpty();
        assertThat(client.getStats(START, END, List.of(), false)).isEmpty();
        server.verify();
    }

    @Test
    void preservesHttpStatusOnFailure() {
        server.expect(requestTo("http://localhost:9090/hit"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("invalid hit"));
        assertThatThrownBy(() -> client.saveHit(new EndpointHit()))
                .isInstanceOfSatisfying(StatsClientException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(400);
                    assertThat(error.getMessage()).contains("invalid hit");
                });
        server.verify();
    }

    @Test
    void doesNotHideConnectionErrors() {
        server.expect(requestTo("http://localhost:9090/hit"))
                .andRespond(withException(new IOException("Connection refused")));
        assertThatThrownBy(() -> client.saveHit(new EndpointHit()))
                .isInstanceOfSatisfying(StatsClientException.class, error -> {
                    assertThat(error.getStatusCode()).isNull();
                    assertThat(error.getCause()).isNotNull();
                });
        server.verify();
    }

    @Test
    void doesNotTreatMissingResponseBodyAsEmptyStats() {
        server.expect(requestTo("http://localhost:9090/stats?start=2026-11-10%2000%3A00%3A00"
                        + "&end=2026-11-11%2000%3A00%3A00&unique=false"))
                .andRespond(withSuccess());
        assertThatThrownBy(() -> client.getStats(START, END, null, false))
                .isInstanceOf(StatsClientException.class).hasMessageContaining("empty response body");
        server.verify();
    }

    @Test
    void validatesAddressAndDateRange() {
        assertThat(new StatsClient("http://localhost:9090")).isNotNull();
        assertThatThrownBy(() -> new StatsClient("file:///tmp"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StatsClient("http://localhost:9090?x=1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getStats(END, START, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
