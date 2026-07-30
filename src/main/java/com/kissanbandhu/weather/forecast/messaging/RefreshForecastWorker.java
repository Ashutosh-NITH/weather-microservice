package com.kissanbandhu.weather.forecast.messaging;

import com.kissanbandhu.weather.common.client.OpenWeatherClient;
import com.kissanbandhu.weather.common.client.dto.OpenWeatherForecastResponse;
import com.kissanbandhu.weather.common.lock.DistributedLockService;
import com.kissanbandhu.weather.forecast.dto.ForecastResponseDto;
import com.kissanbandhu.weather.forecast.messaging.event.PersistForecastEvent;
import com.kissanbandhu.weather.forecast.messaging.event.RefreshForecastEvent;
import com.kissanbandhu.weather.forecast.service.ForecastMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshForecastWorker {

    private static final Logger log = LoggerFactory.getLogger(RefreshForecastWorker.class);
    private static final String CACHE_PREFIX = "forecast:weather:";
    private static final String LOCK_PREFIX = "forecast:";

    private final OpenWeatherClient openWeatherClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedLockService lockService;
    private final ForecastEventPublisher eventPublisher;
    private final ForecastMapper mapper;
    private final long cacheTtlMinutes;

    public RefreshForecastWorker(OpenWeatherClient openWeatherClient,
                                  RedisTemplate<String, Object> redisTemplate,
                                  DistributedLockService lockService,
                                  ForecastEventPublisher eventPublisher,
                                  ForecastMapper mapper,
                                  @Value("${weather.cache.forecast.ttl-minutes:370}") long cacheTtlMinutes) {
        this.openWeatherClient = openWeatherClient;
        this.redisTemplate = redisTemplate;
        this.lockService = lockService;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    @RabbitListener(queues = "forecast.refresh.queue", containerFactory = "retryingListenerFactory")
    public void onRefreshRequested(RefreshForecastEvent event) {
        String geohash = event.geohash();
        String lockKey = LOCK_PREFIX + geohash;

        String lockToken = lockService.tryLock(lockKey);
        if (lockToken == null) {
            log.debug("Skipping scheduled forecast refresh for geohash={} - already locked", geohash);
            return;
        }

        try {
            log.info("Refreshing forecast for geohash={}", geohash);
            OpenWeatherForecastResponse response =
                openWeatherClient.fetchForecast(event.latitude(), event.longitude());

            ForecastResponseDto dto = mapper.toDto(response, geohash, event.latitude(), event.longitude(), "OPENWEATHER");

            redisTemplate.opsForValue().set(CACHE_PREFIX + geohash, dto, Duration.ofMinutes(cacheTtlMinutes));
            eventPublisher.publishPersistRequested(new PersistForecastEvent(dto));
        } finally {
            lockService.release(lockKey, lockToken);
        }
    }
}
