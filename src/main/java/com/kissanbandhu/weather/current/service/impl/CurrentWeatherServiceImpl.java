package com.kissanbandhu.weather.current.service.impl;

import com.kissanbandhu.weather.common.client.OpenWeatherClient;
import com.kissanbandhu.weather.common.client.dto.OpenWeatherCurrentResponse;
import com.kissanbandhu.weather.common.config.PerClientRateLimiter;
import com.kissanbandhu.weather.common.entity.TrackedLocation;
import com.kissanbandhu.weather.common.entity.TrackedLocationRepository;
import com.kissanbandhu.weather.common.exception.ExternalApiException;
import com.kissanbandhu.weather.common.exception.RateLimitExceededException;
import com.kissanbandhu.weather.common.geohash.GeoHashService;
import com.kissanbandhu.weather.common.lock.DistributedLockService;
import com.kissanbandhu.weather.current.dto.CurrentWeatherResponseDto;
import com.kissanbandhu.weather.current.entity.CurrentWeather;
import com.kissanbandhu.weather.current.messaging.WeatherEventPublisher;
import com.kissanbandhu.weather.current.messaging.event.PersistWeatherEvent;
import com.kissanbandhu.weather.current.repository.CurrentWeatherRepository;
import com.kissanbandhu.weather.current.service.CurrentWeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class CurrentWeatherServiceImpl implements CurrentWeatherService {

    private static final Logger log = LoggerFactory.getLogger(CurrentWeatherServiceImpl.class);
    private static final String CACHE_PREFIX = "current:weather:";

    private final PerClientRateLimiter rateLimiter;
    private final GeoHashService geoHashService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedLockService lockService;
    private final CurrentWeatherRepository currentWeatherRepository;
    private final TrackedLocationRepository trackedLocationRepository;
    private final OpenWeatherClient openWeatherClient;
    private final WeatherEventPublisher eventPublisher;
    private final long cacheTtlMinutes;

    public CurrentWeatherServiceImpl(PerClientRateLimiter rateLimiter,
                                      GeoHashService geoHashService,
                                      RedisTemplate<String, Object> redisTemplate,
                                      DistributedLockService lockService,
                                      CurrentWeatherRepository currentWeatherRepository,
                                      TrackedLocationRepository trackedLocationRepository,
                                      OpenWeatherClient openWeatherClient,
                                      WeatherEventPublisher eventPublisher,
                                      @Value("${weather.cache.current.ttl-minutes:35}") long cacheTtlMinutes) {
        this.rateLimiter = rateLimiter;
        this.geoHashService = geoHashService;
        this.redisTemplate = redisTemplate;
        this.lockService = lockService;
        this.currentWeatherRepository = currentWeatherRepository;
        this.trackedLocationRepository = trackedLocationRepository;
        this.openWeatherClient = openWeatherClient;
        this.eventPublisher = eventPublisher;
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    @Override
    public CurrentWeatherResponseDto getCurrentWeather(double latitude, double longitude, String clientKey) {
        if (!rateLimiter.tryAcquire(clientKey, "current")) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded. 10 requests per minute per client .Please try again later.");
        }

        String geohash = geoHashService.encode(latitude, longitude);
        String cacheKey = CACHE_PREFIX + geohash;

        touchTrackedLocation(geohash, latitude, longitude);

        CurrentWeatherResponseDto cached = (CurrentWeatherResponseDto) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for geohash={}", geohash);
            return cached;
        }

        log.debug("Cache miss for geohash={}, checking DB", geohash);
        Optional<CurrentWeather> dbRow = currentWeatherRepository.findByGeohash(geohash);
        if (dbRow.isPresent() && isFresh(dbRow.get().getUpdatedAt())) {
            CurrentWeatherResponseDto dto = toDto(dbRow.get(), "DB");
            cacheResult(cacheKey, dto);
            return dto;
        }

        // Neither Redis nor a fresh DB row - need to hit OpenWeather. Guard with a
        // distributed lock so concurrent requests for the same cell don't all fan
        // out to the upstream API at once.
        String lockToken = lockService.tryLock(geohash);
        if (lockToken == null) {
            // Someone else is refreshing this cell right now - serve whatever we have
            // rather than making the caller wait or double-calling OpenWeather.
            if (dbRow.isPresent()) {
                log.debug("Lock held by another request for geohash={}, serving stale DB row", geohash);
                return toDto(dbRow.get(), "DB");
            }
            throw new ExternalApiException(
                "Weather data for this location is currently being refreshed, please retry shortly");
        }

        try {
            OpenWeatherCurrentResponse response = openWeatherClient.fetchCurrentWeather(latitude, longitude);
            CurrentWeatherResponseDto dto = toDto(response, geohash, latitude, longitude);

            cacheResult(cacheKey, dto); // update Redis synchronously
            eventPublisher.publishPersistRequested(new PersistWeatherEvent(dto)); // DB write is async

            return dto;
        } finally {
            lockService.release(geohash, lockToken);
        }
    }

    private void touchTrackedLocation(String geohash, double latitude, double longitude) {
        trackedLocationRepository.findByGeohash(geohash)
            .ifPresentOrElse(
                loc -> {
                    loc.setLastRequestedAt(Instant.now());
                    trackedLocationRepository.save(loc);
                },
                () -> trackedLocationRepository.save(TrackedLocation.builder()
                    .geohash(geohash)
                    .latitude(latitude)
                    .longitude(longitude)
                    .lastRequestedAt(Instant.now())
                    .build())
            );
    }

    private boolean isFresh(Instant updatedAt) {
        return updatedAt.isAfter(Instant.now().minus(Duration.ofMinutes(cacheTtlMinutes)));
    }

    private void cacheResult(String cacheKey, CurrentWeatherResponseDto dto) {
        redisTemplate.opsForValue().set(cacheKey, dto, Duration.ofMinutes(cacheTtlMinutes));
    }

    private CurrentWeatherResponseDto toDto(CurrentWeather entity, String source) {
        return new CurrentWeatherResponseDto(
            entity.getGeohash(), entity.getLatitude(), entity.getLongitude(),
            entity.getTemperatureCelsius(), entity.getFeelsLikeCelsius(), entity.getHumidityPercent(),
            entity.getPressureHpa(), entity.getWindSpeedMps(), entity.getWindDirectionDeg(),
            entity.getCloudinessPercent(), entity.getWeatherMain(), entity.getWeatherDescription(),
            entity.getWeatherIcon(), entity.getObservedAt(), entity.getFetchedAt(), source
        );
    }

    private CurrentWeatherResponseDto toDto(OpenWeatherCurrentResponse response, String geohash,
                                             double latitude, double longitude) {
        var weather = response.weather() != null && !response.weather().isEmpty()
            ? response.weather().get(0) : null;
        var main = response.main();
        var wind = response.wind();
        var clouds = response.clouds();

        return new CurrentWeatherResponseDto(
            geohash, latitude, longitude,
            main != null ? main.temp() : null,
            main != null ? main.feelsLike() : null,
            main != null ? main.humidity() : null,
            main != null ? main.pressure() : null,
            wind != null ? wind.speed() : null,
            wind != null ? wind.direction() : null,
            clouds != null ? clouds.all() : null,
            weather != null ? weather.main() : null,
            weather != null ? weather.description() : null,
            weather != null ? weather.icon() : null,
            Instant.ofEpochSecond(response.dt()),
            Instant.now(),
            "OPENWEATHER"
        );
    }
}
