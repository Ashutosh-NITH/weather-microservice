package com.kissanbandhu.weather.history.messaging;

import com.kissanbandhu.weather.history.entity.HistoryMonthSummary;
import com.kissanbandhu.weather.history.messaging.event.RefreshMonthSummaryEvent;
import com.kissanbandhu.weather.history.repository.HistoryMonthSummaryRepository;
import com.kissanbandhu.weather.history.repository.MonthlyAggregate;
import com.kissanbandhu.weather.history.repository.WeatherHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;

@Component
public class HistoryMonthSummaryWorker {

    private static final Logger log = LoggerFactory.getLogger(HistoryMonthSummaryWorker.class);

    private final WeatherHistoryRepository weatherHistoryRepository;
    private final HistoryMonthSummaryRepository summaryRepository;

    public HistoryMonthSummaryWorker(WeatherHistoryRepository weatherHistoryRepository,
                                      HistoryMonthSummaryRepository summaryRepository) {
        this.weatherHistoryRepository = weatherHistoryRepository;
        this.summaryRepository = summaryRepository;
    }

    @RabbitListener(queues = "history.month.summary.queue", containerFactory = "retryingListenerFactory")
    @Transactional
    public void onRefreshMonthSummaryRequested(RefreshMonthSummaryEvent event) {
        YearMonth yearMonth = YearMonth.of(event.year(), event.month());
        var from = yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        MonthlyAggregate aggregate = weatherHistoryRepository.aggregateForMonth(event.geohash(), from, to);
        if (aggregate == null || aggregate.sampleCount() == 0) {
            log.debug("No history samples for geohash={} {}-{}, skipping summary", event.geohash(), event.year(), event.month());
            return;
        }

        HistoryMonthSummary summary = summaryRepository
            .findByGeohashAndYearAndMonth(event.geohash(), event.year(), event.month())
            .orElseGet(() -> HistoryMonthSummary.builder()
                .geohash(event.geohash())
                .year(event.year())
                .month(event.month())
                .build());

        summary.setAvgTemperatureCelsius(aggregate.avgTemperatureCelsius());
        summary.setMinTemperatureCelsius(aggregate.minTemperatureCelsius());
        summary.setMaxTemperatureCelsius(aggregate.maxTemperatureCelsius());
        summary.setAvgHumidityPercent(aggregate.avgHumidityPercent());
        summary.setSampleCount((int) aggregate.sampleCount());

        summaryRepository.save(summary);
        log.info("Updated month summary for geohash={} {}-{} ({} samples)",
            event.geohash(), event.year(), event.month(), aggregate.sampleCount());
    }
}
