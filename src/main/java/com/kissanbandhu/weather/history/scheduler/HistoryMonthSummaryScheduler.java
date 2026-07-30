package com.kissanbandhu.weather.history.scheduler;

import com.kissanbandhu.weather.common.lock.DistributedLockService;
import com.kissanbandhu.weather.history.messaging.HistoryEventPublisher;
import com.kissanbandhu.weather.history.messaging.event.RefreshMonthSummaryEvent;
import com.kissanbandhu.weather.history.repository.WeatherHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class HistoryMonthSummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HistoryMonthSummaryScheduler.class);
    private static final String SCHEDULER_LOCK_KEY = "scheduler:history:month-summary";

    private final WeatherHistoryRepository weatherHistoryRepository;
    private final HistoryEventPublisher eventPublisher;
    private final DistributedLockService lockService;

    public HistoryMonthSummaryScheduler(WeatherHistoryRepository weatherHistoryRepository,
                                         HistoryEventPublisher eventPublisher,
                                         DistributedLockService lockService) {
        this.weatherHistoryRepository = weatherHistoryRepository;
        this.eventPublisher = eventPublisher;
        this.lockService = lockService;
    }

    @Scheduled(cron = "${weather.scheduler.history.month-summary-cron}")
    public void fanOutMonthSummaryRefresh() {
        String token = lockService.tryLock(SCHEDULER_LOCK_KEY, Duration.ofHours(1));
        if (token == null) {
            log.debug("Another replica is already running the month-summary scheduler tick, skipping");
            return;
        }

        try {
            YearMonth previousMonth = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
            var from = previousMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            var to = previousMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

            List<String> geohashes = weatherHistoryRepository.findDistinctGeohashesInRange(from, to);
            for (String geohash : geohashes) {
                eventPublisher.publishRefreshMonthSummary(new RefreshMonthSummaryEvent(
                    geohash, previousMonth.getYear(), previousMonth.getMonthValue()));
            }

            log.info("History month-summary scheduler: {} geohashes queued for {}-{}",
                geohashes.size(), previousMonth.getYear(), previousMonth.getMonthValue());
        } finally {
            lockService.release(SCHEDULER_LOCK_KEY, token);
        }
    }
}
