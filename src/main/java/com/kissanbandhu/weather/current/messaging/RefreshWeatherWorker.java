package com.kissanbandhu.weather.current.messaging;

import com.kissanbandhu.weather.common.client.OpenWeatherClient;
import com.kissanbandhu.weather.common.client.dto.OpenWeatherCurrentResponse;
import com.kissanbandhu.weather.common.lock.DistributedLockService;
import com.kissanbandhu.weather.current.dto.CurrentWeatherResponseDto;
import com.kissanbandhu.weather.current.messaging.event.PersistWeatherEvent;
import com.kissanbandhu.weather.current.messaging.event.RefreshWeatherEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;

/**
 * Background twin of the synchronous cache-miss path in CurrentWeatherServiceImpl,
 * but triggered by RefreshScheduler instead of a user request. Runs for every
 * "active" geohash cell (one a farmer has actually requested) whose data has
 * gone stale, keeping the cache warm ahead of the next read.
 */
@Component
public class RefreshWeatherWorker {

    private static final Logger log = LoggerFactory.getLogger(RefreshWeatherWorker.class);
    private static final String CACHE_PREFIX = "current:weather:";

    private final OpenWeatherClient openWeatherClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedLockService lockService;
    private final WeatherEventPublisher eventPublisher;
    private final long cacheTtlMinutes;

    public RefreshWeatherWorker(OpenWeatherClient openWeatherClient,
                                RedisTemplate<String, Object> redisTemplate,
                                DistributedLockService lockService,
                                WeatherEventPublisher eventPublisher,
                                @Value("${weather.cache.current.ttl-minutes:35}")
                                long cacheTtlMinutes) {
        this.openWeatherClient = openWeatherClient;
        this.redisTemplate = redisTemplate;
        this.lockService = lockService;
        this.eventPublisher = eventPublisher;
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    @RabbitListener(queues = "current.refresh.queue", containerFactory = "retryingListenerFactory")
    public void onRefreshRequested(RefreshWeatherEvent event) {
        String geohash = event.geohash();

        // Scheduler may fan out many cells at once; skip cleanly if another
        // instance (or the live-read path) is already refreshing this one.
        String lockToken = lockService.tryLock(geohash);
        if (lockToken == null) {
            log.debug("Skipping scheduled refresh for geohash={} - already locked", geohash);
            return;
        }

        try {
            log.info("Refreshing weather for geohash={}", geohash);
            OpenWeatherCurrentResponse response =
                openWeatherClient.fetchCurrentWeather(event.latitude(), event.longitude());

            CurrentWeatherResponseDto dto = mapToDto(response, geohash, event.latitude(), event.longitude());

            redisTemplate.opsForValue().set(CACHE_PREFIX + geohash, dto, Duration.ofMinutes(cacheTtlMinutes));
            eventPublisher.publishPersistRequested(new PersistWeatherEvent(dto));
        } finally {
            lockService.release(geohash, lockToken);
        }
    }

    private CurrentWeatherResponseDto mapToDto(OpenWeatherCurrentResponse response, String geohash,
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
