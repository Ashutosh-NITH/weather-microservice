package com.kissanbandhu.weather.common.exception;

/**
 * Thrown by the per-client request rate limiter (1 request / 15 min per the
 * diagram) and separately used to translate Resilience4j's own
 * RequestNotPermitted (when the OpenWeather-side limiter trips) into a
 * consistent 429 response.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
