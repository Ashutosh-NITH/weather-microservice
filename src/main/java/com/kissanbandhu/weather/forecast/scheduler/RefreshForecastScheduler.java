package com.kissanbandhu.weather.forecast.scheduler;

import com.kissanbandhu.weather.common.entity.TrackedLocation;
import com.kissanbandhu.weather.common.entity.TrackedLocationRepository;
import com.kissanbandhu.weather.common.lock.DistributedLockService;
import com.kissanbandhu.weather.forecast.messaging.ForecastEventPublisher;
import com.kissanbandhu.weather.forecast.messaging.event.RefreshForecastEvent;
import com.kissanbandhu.weather.forecast.repository.ForecastWeatherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Same single-replica-safe pattern as current.RefreshScheduler, on a 6-hour
 * cron instead of 25 minutes. Reuses tracked_location (shared across domains)
 * to find active cells, and forecast_weather to find stale ones.
 */
@Component
public class RefreshForecastScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshForecastScheduler.class);
    private static final String SCHEDULER_LOCK_KEY = "scheduler:forecast:tick";

    private final TrackedLocationRepository trackedLocationRepository;
    private final ForecastWeatherRepository forecastWeatherRepository;
    private final ForecastEventPublisher eventPublisher;
    private final DistributedLockService lockService;
    private final int staleAfterMinutes;

    public RefreshForecastScheduler(TrackedLocationRepository trackedLocationRepository,
                                     ForecastWeatherRepository forecastWeatherRepository,
                                     ForecastEventPublisher eventPublisher,
                                     DistributedLockService lockService,
                                     @Value("${weather.scheduler.forecast.stale-after-minutes:360}") int staleAfterMinutes) {
        this.trackedLocationRepository = trackedLocationRepository;
        this.forecastWeatherRepository = forecastWeatherRepository;
        this.eventPublisher = eventPublisher;
        this.lockService = lockService;
        this.staleAfterMinutes = staleAfterMinutes;
    }

    @Scheduled(cron = "${weather.scheduler.forecast.cron}")
    public void refreshStaleActiveCells() {
        // TTL shorter than the 6h scheduler period so a crashed replica can't
        // wedge future ticks - same reasoning as the current-weather scheduler.
        String token = lockService.tryLock(SCHEDULER_LOCK_KEY, Duration.ofHours(5));
        if (token == null) {
            log.debug("Another replica is already running the forecast scheduler tick, skipping");
            return;
        }

        try {
            Instant staleBefore = Instant.now().minus(Duration.ofMinutes(staleAfterMinutes));
            Set<String> staleGeohashes = Set.copyOf(forecastWeatherRepository.findStaleGeohashes(staleBefore));
            List<TrackedLocation> activeLocations = trackedLocationRepository.findAll();

            int enqueued = 0;
            for (TrackedLocation location : activeLocations) {
                boolean neverFetched = forecastWeatherRepository.findByGeohash(location.getGeohash()).isEmpty();
                if (neverFetched || staleGeohashes.contains(location.getGeohash())) {
                    eventPublisher.publishRefreshRequested(new RefreshForecastEvent(
                        location.getGeohash(), location.getLatitude(), location.getLongitude()));
                    enqueued++;
                }
            }

            log.info("Forecast scheduler tick complete: {} active cells checked, {} refresh events enqueued",
                activeLocations.size(), enqueued);
        } finally {
            lockService.release(SCHEDULER_LOCK_KEY, token);
        }
    }
}
