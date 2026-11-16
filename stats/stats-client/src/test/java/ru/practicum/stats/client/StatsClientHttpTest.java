package ru.practicum.stats.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StatsClientHttpTest {
    @Test
    void defaultClientCommunicatesOverHttpWithJavaTimeDto() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> hitBody = new AtomicReference<>();
        AtomicReference<String> statsQuery = new AtomicReference<>();
        server.createContext("/hit", exchange -> {
            hitBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.createContext("/stats", exchange -> {
            statsQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "[{\"app\":\"ewm\",\"uri\":\"/events/1\",\"hits\":1}]"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            StatsClient client = new StatsClient("http://127.0.0.1:" + server.getAddress().getPort());
            LocalDateTime time = LocalDateTime.of(2026, 11, 10, 12, 0);
            client.saveHit(new EndpointHit(null, "ewm", "/events/1", "::1", time));
            assertThat(hitBody.get()).contains("\"timestamp\":\"2026-11-10 12:00:00\"");
            assertThat(client.getStats(time, time, List.of("/events/1"), true))
                    .containsExactly(new ViewStats("ewm", "/events/1", 1L));
            assertThat(statsQuery.get()).contains("start=2026-11-10%2012%3A00%3A00", "unique=true");
        } finally {
            server.stop(0);
        }
    }
}
