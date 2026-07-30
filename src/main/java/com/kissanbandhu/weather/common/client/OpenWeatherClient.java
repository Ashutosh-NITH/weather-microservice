package com.kissanbandhu.weather.common.client;

import com.kissanbandhu.weather.common.client.dto.OpenWeatherCurrentResponse;
import com.kissanbandhu.weather.common.client.dto.OpenWeatherForecastResponse;
import com.kissanbandhu.weather.common.exception.ExternalApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Thin wrapper around OpenWeather's free-tier "current weather" endpoint.
 *
 * All three Resilience4j policies from application.yml (instance name
 * "openWeatherCurrent") apply, in this order at call time:
 *   1. RateLimiter   - caps outbound calls at 55/min (OpenWeather free tier = 60/min)
 *   2. CircuitBreaker - trips after sustained failures, fails fast while OPEN
 *   3. Retry          - retries transient failures with exponential backoff
 *
 * Order matters: rate limiter is the outermost annotation processed first by
 * the AOP proxy chain (annotations closest to the method run last), so put
 * RateLimiter above CircuitBreaker above Retry to get "don't even attempt the
 * call if we're over budget" -> "don't call a known-broken upstream" -> "retry
 * genuinely transient failures" semantics.
 */
@Component
public class OpenWeatherClient {

    private static final Logger log = LoggerFactory.getLogger(OpenWeatherClient.class);
    private static final String CURRENT_INSTANCE = "openWeatherCurrent";
    private static final String FORECAST_INSTANCE = "openWeatherForecast";

    private final WebClient webClient;
    private final String apiKey;
    private final String currentPath;
    private final String forecastPath;
    private final String units;

    public OpenWeatherClient(WebClient openWeatherWebClient,
                              @Value("${openweather.api.key}") String apiKey,
                              @Value("${openweather.api.current-path}") String currentPath,
                              @Value("${openweather.api.forecast-path:/data/2.5/forecast}") String forecastPath,
                              @Value("${openweather.api.units:metric}") String units) {
        this.webClient = openWeatherWebClient;
        this.apiKey = apiKey;
        this.currentPath = currentPath;
        this.forecastPath = forecastPath;
        this.units = units;
    }

    @RateLimiter(name = CURRENT_INSTANCE)
    @CircuitBreaker(name = CURRENT_INSTANCE)
    @Retry(name = CURRENT_INSTANCE)
    public OpenWeatherCurrentResponse fetchCurrentWeather(double latitude, double longitude) {
        try {
            return webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(currentPath)
                    .queryParam("lat", latitude)
                    .queryParam("lon", longitude)
                    .queryParam("appid", apiKey)
                    .queryParam("units", units)
                    .build())
                .retrieve()
                .bodyToMono(OpenWeatherCurrentResponse.class)
                .block();
        } catch (WebClientResponseException ex) {
            log.error("OpenWeather returned {} for lat={}, lon={}: {}",
                ex.getStatusCode(), latitude, longitude, ex.getResponseBodyAsString());
            throw new ExternalApiException("OpenWeather API error: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            log.error("OpenWeather call failed for lat={}, lon={}", latitude, longitude, ex);
            throw new ExternalApiException("OpenWeather call failed", ex);
        }
    }

    /**
     * Free-tier 5-day/3-hour-step forecast. Kept as a separate Resilience4j
     * instance (openWeatherForecast) from fetchCurrentWeather so a forecast
     * outage/slowdown can trip its own circuit breaker without also blocking
     * current-weather calls, even though both share the same OpenWeather
     * account and the same 60 calls/min free-tier budget.
     */
    @RateLimiter(name = FORECAST_INSTANCE)
    @CircuitBreaker(name = FORECAST_INSTANCE)
    @Retry(name = FORECAST_INSTANCE)
    public OpenWeatherForecastResponse fetchForecast(double latitude, double longitude) {
        try {
            return webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(forecastPath)
                    .queryParam("lat", latitude)
                    .queryParam("lon", longitude)
                    .queryParam("appid", apiKey)
                    .queryParam("units", units)
                    .build())
                .retrieve()
                .bodyToMono(OpenWeatherForecastResponse.class)
                .block();
        } catch (WebClientResponseException ex) {
            log.error("OpenWeather forecast returned {} for lat={}, lon={}: {}",
                ex.getStatusCode(), latitude, longitude, ex.getResponseBodyAsString());
            throw new ExternalApiException("OpenWeather forecast API error: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            log.error("OpenWeather forecast call failed for lat={}, lon={}", latitude, longitude, ex);
            throw new ExternalApiException("OpenWeather forecast call failed", ex);
        }
    }
}
