package ru.practicum.ewm.main.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.model.*;
import ru.practicum.ewm.main.repository.*;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.client.StatsClientException;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

@Service
public class EventStatisticsService {
    private final StatsClient stats;

    public EventStatisticsService(StatsClient stats) {
        this.stats = stats;
    }

    public Map<Long, Long> views(Collection<EventEntity> list) {
        if (list.isEmpty())
            return Map.of();
        List<String> uris = list.stream().map(e -> "/events/" + e.getId()).toList();
        try {
            List<ViewStats> found = stats.getStats(LocalDateTime.of(2000, 1, 1, 0, 0),
                    LocalDateTime.now().plusSeconds(1), uris, true);
            return found.stream()
                    .collect(Collectors.toMap(s -> parseEventId(s.getUri()), ViewStats::getHits, (a, b) -> a));
        } catch (StatsClientException | IllegalArgumentException e) {
            return Map.of();
        }
    }

    private Long parseEventId(String uri) {
        try {
            return Long.parseLong(uri.substring(uri.lastIndexOf('/') + 1));
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    public void recordHit(String uri, String ip, String app) {
        try {
            stats.saveHit(new EndpointHit(null, app == null ? "ewm-main-service" : app, uri,
                    ip == null ? "unknown" : ip, LocalDateTime.now()));
        } catch (StatsClientException ignored) {
        }
    }
}
