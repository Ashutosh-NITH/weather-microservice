package com.kissanbandhu.weather.common.exception;

/**
 * Thrown when OpenWeather returns a non-2xx response or the call fails
 * (timeout, connection reset, etc). Registered as a retry-eligible exception
 * for the Resilience4j "openWeatherCurrent" instance in application.yml.
 */
public class ExternalApiException extends RuntimeException {

    public ExternalApiException(String message) {
        super(message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
