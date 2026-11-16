package ru.practicum.stats.client;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StatsClient {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String baseUrl;

    private final RestTemplate restTemplate;

    public StatsClient(String baseUrl) {
        this(baseUrl, new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build());
    }

    StatsClient(String baseUrl, RestTemplate restTemplate) {
        URI address = URI.create(Objects.requireNonNull(baseUrl, "baseUrl"));
        if ((!"http".equals(address.getScheme()) && !"https".equals(address.getScheme()))
                || address.getHost() == null || address.getQuery() != null || address.getFragment() != null) {
            throw new IllegalArgumentException("baseUrl must be an absolute HTTP(S) URL without query or fragment");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
    }

    public void saveHit(EndpointHit hit) {
        Objects.requireNonNull(hit, "hit");
        try {
            restTemplate.postForEntity(URI.create(baseUrl + "/hit"), hit, Void.class);
        } catch (RestClientException exception) {
            throw clientError(exception);
        }
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start must not be after end");
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/stats")
                .queryParam("start", "{start}")
                .queryParam("end", "{end}")
                .queryParam("unique", unique);
        Map<String, Object> variables = new HashMap<>();
        variables.put("start", start.format(DATE_FORMAT));
        variables.put("end", end.format(DATE_FORMAT));
        if (uris != null) {
            for (int i = 0; i < uris.size(); i++) {
                String variable = "uri" + i;
                builder.queryParam("uris", "{" + variable + "}");
                variables.put(variable, Objects.requireNonNull(uris.get(i), "uri"));
            }
        }
        URI address = builder.encode().buildAndExpand(variables).toUri();
        try {
            ResponseEntity<List<ViewStats>> response = restTemplate.exchange(address, HttpMethod.GET,
                    HttpEntity.EMPTY, new ParameterizedTypeReference<>() { });
            if (response.getBody() == null) {
                throw new StatsClientException("Statistics service returned an empty response body", null, null);
            }
            return response.getBody();
        } catch (RestClientException exception) {
            throw clientError(exception);
        }
    }

    private StatsClientException clientError(RestClientException exception) {
        Integer status = exception instanceof RestClientResponseException response
                ? response.getStatusCode().value() : null;
        return new StatsClientException("Statistics request failed: " + exception.getMessage(), status, exception);
    }
}
