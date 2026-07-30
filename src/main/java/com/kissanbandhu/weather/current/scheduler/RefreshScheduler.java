package com.kissanbandhu.weather.current.scheduler;

import com.kissanbandhu.weather.common.entity.TrackedLocation;
import com.kissanbandhu.weather.common.entity.TrackedLocationRepository;
import com.kissanbandhu.weather.common.lock.DistributedLockService;
import com.kissanbandhu.weather.current.messaging.WeatherEventPublisher;
import com.kissanbandhu.weather.current.messaging.event.RefreshWeatherEvent;
import com.kissanbandhu.weather.current.repository.CurrentWeatherRepository;
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
 * Fires every 25 minutes (weather.scheduler.current.cron). Finds geohash
 * cells that are "active" (a farmer requested them recently - tracked_location)
 * AND stale (current_weather row not updated in the last 30 minutes), then
 * publishes one RefreshWeatherEvent per cell so RefreshWeatherWorker can pull
 * fresh data ahead of the next farmer read.
 *
 * A single Redis lock ("scheduler:current:tick") guards the whole tick, not
 * per-cell, so that when this service is scaled horizontally only ONE replica
 * runs a given scheduling tick - replicas don't all enqueue duplicate refresh
 * events for the same cell simultaneously.
 */
@Component
public class RefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshScheduler.class);
    private static final String SCHEDULER_LOCK_KEY = "scheduler:current:tick";

    private final TrackedLocationRepository trackedLocationRepository;
    private final CurrentWeatherRepository currentWeatherRepository;
    private final WeatherEventPublisher eventPublisher;
    private final DistributedLockService lockService;
    private final int staleAfterMinutes;

    public RefreshScheduler(TrackedLocationRepository trackedLocationRepository,
                             CurrentWeatherRepository currentWeatherRepository,
                             WeatherEventPublisher eventPublisher,
                             DistributedLockService lockService,
                             @Value("${weather.scheduler.current.stale-after-minutes:30}") int staleAfterMinutes) {
        this.trackedLocationRepository = trackedLocationRepository;
        this.currentWeatherRepository = currentWeatherRepository;
        this.eventPublisher = eventPublisher;
        this.lockService = lockService;
        this.staleAfterMinutes = staleAfterMinutes;
    }

    @Scheduled(cron = "${weather.scheduler.current.cron}")
    public void refreshStaleActiveCells() {
        // Lock TTL a little shorter than the scheduler period so a crashed
        // replica can't wedge every future tick indefinitely.
        String token = lockService.tryLock(SCHEDULER_LOCK_KEY, Duration.ofMinutes(20));
        if (token == null) {
            log.debug("Another replica is already running this scheduler tick, skipping");
            return;
        }

        try {
            Instant staleBefore = Instant.now().minus(Duration.ofMinutes(staleAfterMinutes));
            List<String> staleGeohashes = currentWeatherRepository.findStaleGeohashes(staleBefore);
            Set<String> staleSet = Set.copyOf(staleGeohashes);

            List<TrackedLocation> activeLocations = trackedLocationRepository.findAll();

            int enqueued = 0;
            for (TrackedLocation location : activeLocations) {
                // Refresh if we've never fetched this cell at all, or the row we
                // have for it is stale. findStaleGeohashes only covers rows that
                // already exist, so "never fetched" is checked separately.
                boolean neverFetched = currentWeatherRepository.findByGeohash(location.getGeohash()).isEmpty();
                if (neverFetched || staleSet.contains(location.getGeohash())) {
                    eventPublisher.publishRefreshRequested(new RefreshWeatherEvent(
                        location.getGeohash(), location.getLatitude(), location.getLongitude()));
                    enqueued++;
                }
            }

            log.info("Scheduler tick complete: {} active cells checked, {} refresh events enqueued",
                activeLocations.size(), enqueued);
        } finally {
            lockService.release(SCHEDULER_LOCK_KEY, token);
        }
    }
}
