package ru.practicum.stats.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;
import ru.practicum.stats.model.Hit;
import ru.practicum.stats.repository.HitRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {
    private final HitRepository repository;

    @Transactional
    public void saveHit(EndpointHit hit) {
        repository.save(new Hit(hit.getApp(), hit.getUri(), hit.getIp(), hit.getTimestamp()));
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start must not be after end");
        }
        boolean allUris = uris == null || uris.isEmpty();
        // Always bind a nonempty IN list; PostgreSQL does not support IN ().
        List<String> selectedUris = allUris ? List.of("") : uris;
        return unique
                ? repository.getUniqueStats(start, end, selectedUris, allUris)
                : repository.getStats(start, end, selectedUris, allUris);
    }
}
