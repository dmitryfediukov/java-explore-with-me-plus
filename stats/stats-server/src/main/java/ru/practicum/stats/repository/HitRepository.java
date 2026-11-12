package ru.practicum.stats.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.stats.dto.ViewStats;
import ru.practicum.stats.model.Hit;

import java.time.LocalDateTime;
import java.util.List;

public interface HitRepository extends JpaRepository<Hit, Long> {
    @Query("""
            select new ru.practicum.stats.dto.ViewStats(h.app, h.uri, count(h.id))
            from Hit h
            where h.timestamp between :start and :end
              and (:allUris = true or h.uri in :uris)
            group by h.app, h.uri
            order by count(h.id) desc, h.app asc, h.uri asc
            """)
    List<ViewStats> getStats(@Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end,
                            @Param("uris") List<String> uris,
                            @Param("allUris") boolean allUris);

    @Query("""
            select new ru.practicum.stats.dto.ViewStats(h.app, h.uri, count(distinct h.ip))
            from Hit h
            where h.timestamp between :start and :end
              and (:allUris = true or h.uri in :uris)
            group by h.app, h.uri
            order by count(distinct h.ip) desc, h.app asc, h.uri asc
            """)
    List<ViewStats> getUniqueStats(@Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end,
                                  @Param("uris") List<String> uris,
                                  @Param("allUris") boolean allUris);
}
