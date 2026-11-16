package ru.practicum.stats.client;

public class StatsClientException extends RuntimeException {
    private final Integer statusCode;

    public StatsClientException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
