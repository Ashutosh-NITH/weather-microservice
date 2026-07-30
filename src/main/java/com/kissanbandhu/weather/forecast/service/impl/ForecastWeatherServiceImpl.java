package com.kissanbandhu.weather.forecast.service.impl;

import com.kissanbandhu.weather.common.client.OpenWeatherClient;
import com.kissanbandhu.weather.common.client.dto.OpenWeatherForecastResponse;
import com.kissanbandhu.weather.common.config.PerClientRateLimiter;
import com.kissanbandhu.weather.common.entity.TrackedLocation;
import com.kissanbandhu.weather.common.entity.TrackedLocationRepository;
import com.kissanbandhu.weather.common.exception.ExternalApiException;
import com.kissanbandhu.weather.common.exception.RateLimitExceededException;
import com.kissanbandhu.weather.common.geohash.GeoHashService;
import com.kissanbandhu.weather.common.lock.DistributedLockService;
import com.kissanbandhu.weather.forecast.dto.ForecastResponseDto;
import com.kissanbandhu.weather.forecast.entity.ForecastWeather;
import com.kissanbandhu.weather.forecast.messaging.ForecastEventPublisher;
import com.kissanbandhu.weather.forecast.messaging.event.PersistForecastEvent;
import com.kissanbandhu.weather.forecast.repository.ForecastWeatherRepository;
import com.kissanbandhu.weather.forecast.service.ForecastMapper;
import com.kissanbandhu.weather.forecast.service.ForecastWeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class ForecastWeatherServiceImpl implements ForecastWeatherService {

    private static final Logger log = LoggerFactory.getLogger(ForecastWeatherServiceImpl.class);
    private static final String CACHE_PREFIX = "forecast:weather:";
    private static final String LOCK_PREFIX = "forecast:"; // distinct namespace from current-weather locks

    private final PerClientRateLimiter rateLimiter;
    private final GeoHashService geoHashService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedLockService lockService;
    private final ForecastWeatherRepository forecastWeatherRepository;
    private final TrackedLocationRepository trackedLocationRepository;
    private final OpenWeatherClient openWeatherClient;
    private final ForecastEventPublisher eventPublisher;
    private final ForecastMapper mapper;
    private final long cacheTtlMinutes;

    public ForecastWeatherServiceImpl(PerClientRateLimiter rateLimiter,
                                       GeoHashService geoHashService,
                                       RedisTemplate<String, Object> redisTemplate,
                                       DistributedLockService lockService,
                                       ForecastWeatherRepository forecastWeatherRepository,
                                       TrackedLocationRepository trackedLocationRepository,
                                       OpenWeatherClient openWeatherClient,
                                       ForecastEventPublisher eventPublisher,
                                       ForecastMapper mapper,
                                       @Value("${weather.cache.forecast.ttl-minutes:370}") long cacheTtlMinutes) {
        this.rateLimiter = rateLimiter;
        this.geoHashService = geoHashService;
        this.redisTemplate = redisTemplate;
        this.lockService = lockService;
        this.forecastWeatherRepository = forecastWeatherRepository;
        this.trackedLocationRepository = trackedLocationRepository;
        this.openWeatherClient = openWeatherClient;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    @Override
    public ForecastResponseDto getForecast(double latitude, double longitude, String clientKey) {
        if (!rateLimiter.tryAcquire(clientKey, "forecast")) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded. 10 requests per minute per client .Please try again later.");
        }

        String geohash = geoHashService.encode(latitude, longitude);
        String cacheKey = CACHE_PREFIX + geohash;

        touchTrackedLocation(geohash, latitude, longitude);

        ForecastResponseDto cached = (ForecastResponseDto) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Forecast cache hit for geohash={}", geohash);
            return cached;
        }

        log.debug("Forecast cache miss for geohash={}, checking DB", geohash);
        Optional<ForecastWeather> dbRow = forecastWeatherRepository.findByGeohash(geohash);
        if (dbRow.isPresent() && isFresh(dbRow.get().getUpdatedAt())) {
            ForecastResponseDto dto = toDto(dbRow.get(), "DB");
            cacheResult(cacheKey, dto);
            return dto;
        }

        String lockKey = LOCK_PREFIX + geohash;
        String lockToken = lockService.tryLock(lockKey);
        if (lockToken == null) {
            if (dbRow.isPresent()) {
                log.debug("Forecast lock held by another request for geohash={}, serving stale DB row", geohash);
                return toDto(dbRow.get(), "DB");
            }
            throw new ExternalApiException(
                "Forecast for this location is currently being refreshed, please retry shortly");
        }

        try {
            OpenWeatherForecastResponse response = openWeatherClient.fetchForecast(latitude, longitude);
            ForecastResponseDto dto = mapper.toDto(response, geohash, latitude, longitude, "OPENWEATHER");

            cacheResult(cacheKey, dto); // update Redis synchronously
            eventPublisher.publishPersistRequested(new PersistForecastEvent(dto)); // DB write is async

            return dto;
        } finally {
            lockService.release(lockKey, lockToken);
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

    private void cacheResult(String cacheKey, ForecastResponseDto dto) {
        redisTemplate.opsForValue().set(cacheKey, dto, Duration.ofMinutes(cacheTtlMinutes));
    }

    private ForecastResponseDto toDto(ForecastWeather entity, String source) {
        return new ForecastResponseDto(
            entity.getGeohash(), entity.getLatitude(), entity.getLongitude(),
            mapper.periodsFromJson(entity.getForecastJson()),
            entity.getFetchedAt(), source
        );
    }
}
